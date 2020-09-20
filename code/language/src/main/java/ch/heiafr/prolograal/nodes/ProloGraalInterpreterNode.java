package ch.heiafr.prolograal.nodes;

import ch.heiafr.prolograal.ProloGraalLanguage;
import ch.heiafr.prolograal.exceptions.ProloGraalExistenceError;
import ch.heiafr.prolograal.parser.ProloGraalParseError;
import ch.heiafr.prolograal.parser.ProloGraalParserImpl;
import ch.heiafr.prolograal.runtime.*;
import ch.heiafr.prolograal.treegraphs.TreeGraphNode;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.stream.Collectors;

/**
 * Class representing the Truffle root interpreting node.
 * Contains the logic to handle user queries on the command line.
 * @author Martin Spoto
 * @see ProloGraalParserImpl
 * @see ProloGraalResolverNode
 */
public class ProloGraalInterpreterNode extends RootNode {

   private final ProloGraalLanguage language;
   private final ProloGraalContext context;

   private boolean showGraph = false;

   public ProloGraalInterpreterNode(ProloGraalLanguage language) {
      super(language);
      this.language = language;
      this.context = language.getContextReference().get();
   }

   @Override
   public Object execute(VirtualFrame frame) {
      // get input/output facilities from current context
      PrintWriter writer = new PrintWriter(context.getOutput(), true);
      BufferedReader reader = new BufferedReader(new InputStreamReader(context.getInput(), StandardCharsets.UTF_8));

      // will store the path to the next possible branch of the proof tree for the latest success
      Deque<Integer> currentBranches = new ArrayDeque<>();
      // store the runtime of the latest query so we can reuse it in case of a redo
      ProloGraalRuntime lastRuntime = null;
      TreeGraphNode treeGraphNode = null;
      // used in case of a redo to skip parsing phase
      boolean skipParsing = false;

      while (true) {
         String line = null;

         writer.print("?- ");
         writer.flush();

         try {
           line = reader.readLine();
         } catch (IOException ex) {
            ex.printStackTrace();
            break;
         }

         writer.println();

         if (line == null) {
            // readline returns null if EOF
            break;
         } else if (line.equals("exit.")) {
            writer.println("Exiting.");
            break;
         } else if (line.equals("listing.")) {
            // print all clauses in the context runtime
            writer.println(context.getRuntime().getClauses().values());
            continue;
         } else if (line.startsWith("listing ")) {
            String filter = line.split("[ .]")[1];
            // print all clauses with heads starting with the given filter in the context runtime
            writer.println(
                    context.getRuntime()
                            .getClauses()
                            .values()
                            .stream()
                            .filter(x -> x.get(0)
                                    .getHead()
                                    .toString()
                                    .startsWith(filter))
                            .collect(Collectors.toList()));
            continue;
         } else if (line.equals("redo.")) {
            // triggers a redo instead of a normal parsing
            if (lastRuntime == null) {
               writer.println("no");
               continue;
            }
            skipParsing = true;
         } else if (line.equals("graph.")) {
            System.out.println("graph enabled");
            showGraph = true;
            continue;
         } else if (line.equals("nograph.")) {
            System.out.println("graph disabled");
            treeGraphNode = null;
            showGraph = false;
            continue;
         } else if (line.equals("help") || line.equals("help.")) {
            writer.println(
                  "Available commands :\n" +
                  "\t'exit.'           : Exit this interpreter.\n" +
                  "\t'listing.'        : Print all known clauses.\n" +
                  "\t'listing <name>.' : Print all known clauses starting with <name>.\n" +
                  "\t'redo.'           : Redo the most recent query, finding the next result\n" +
                  "\t                    or returning no if no more are found.\n" +
                  "\t'help.'            : Print this help message."
            );
            continue;
         }

         Source source = null;
         if (!skipParsing) {
            // count the line as a list of goals to resolve
            line = "goals :- " + line;
            // new goal, so clear the current branches
            currentBranches.clear();

            source = Source.newBuilder(ProloGraalLanguage.ID, line, null).build();
         }
         ProloGraalRuntime runtime;
         try {
            if (!skipParsing) {
               // parse the source to get a runtime
               runtime = new ProloGraalRuntime(language.getContextReference().get());
               runtime.addProloGraalClauses(ProloGraalParserImpl.parseClauses(source));
               lastRuntime = runtime;
               treeGraphNode = showGraph ? new TreeGraphNode() : null;
            } else {
               runtime = lastRuntime;
            }
         } catch (ProloGraalParseError parseError) {
            // clear latest runtime on error so we cannot redo
            lastRuntime = null;
            writer.println(parseError.getMessage());
            continue;
         }

         ProloGraalBoolean callResult;
         try {
            skipParsing = false;
            // get the result from the resolver node
            callResult =
                  (ProloGraalBoolean) Truffle.getRuntime()
                  .createCallTarget(context.getResolverNode())
                  .call(runtime, currentBranches, treeGraphNode);
            if (currentBranches.isEmpty()) {
               // there are no more solutions
               lastRuntime = null;
               if (treeGraphNode != null) {
                  System.out.println("============ tree graph:");
                  System.out.println(treeGraphNode);
               }
            }
         } catch (ProloGraalExistenceError existenceError) {
            writer.println("Error : no clause for goal '" + existenceError.getMessage() + "'");
            continue;
         }
         writer.println();

         if (callResult.asBoolean()) {
            // result is a success
            ProloGraalSuccess success = (ProloGraalSuccess) callResult;
            // print all bound variables
            for (ProloGraalVariable variable : success.getVariables().values()) {
               if (!variable.isBound()) {
                  // nothing to display
                  continue;
               }
               ProloGraalTerm<?> root = variable.getRootValue();
               String rootStr;
               // special handling for structure to get prettier output
               if (root instanceof ProloGraalStructure) {
                  rootStr = ((ProloGraalStructure) root).toRootString();
               } else {
                  rootStr = root.toString();
               }
               writer.println(variable.getName() + " = " + rootStr);
            }
         }
         writer.println();
         writer.println(callResult);
      }

      /*
      try {
         reader.close();
      } catch (IOException ex) {
         // ignored
      }
      */
      return new ProloGraalSuccess();
   }

}