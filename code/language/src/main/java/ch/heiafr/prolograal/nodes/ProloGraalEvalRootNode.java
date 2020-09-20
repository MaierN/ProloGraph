package ch.heiafr.prolograal.nodes;

import ch.heiafr.prolograal.ProloGraalLanguage;
import ch.heiafr.prolograal.runtime.*;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

import java.util.*;

/**
 * Class representing the Truffle root node for evaluation of source code.
 * Taken and adapted from the SimpleLanguage demo implementation.
 * @see ProloGraalLanguage
 * @author Martin Spoto
 */
public final class ProloGraalEvalRootNode extends RootNode {

   private final ProloGraalLanguage language;
   private final ProloGraalRuntime runtime;
   @CompilationFinal
   private boolean registered;

   private final List<ProloGraalQuery> queries;
   private final ContextReference<ProloGraalContext> reference;

   public ProloGraalEvalRootNode(ProloGraalLanguage language, ProloGraalRuntime runtime, List<ProloGraalQuery> queries) {
      super(null); // internal frame
      this.runtime = runtime;
      this.language = language;
      this.reference = language.getContextReference();
      this.queries = queries;
   }

   @Override
   public String getName() {
      return "ProloGraalEvalRootNode";
   }

   @Override
   public String toString() {
      return getName();
   }

   @Override
   public Object execute(VirtualFrame frame) {
      /* Lazy registrations of clauses on first execution. */
      if (!registered) {
         /* Function registration is a slow-path operation that must not be compiled. */
         CompilerDirectives.transferToInterpreterAndInvalidate();
         reference.get().registerRuntime(runtime);

         ProloGraalInterpreterNode interpreterNode = new ProloGraalInterpreterNode(language);
         ProloGraalResolverNode resolverNode = new ProloGraalResolverNode(language);

         reference.get().registerInterpreter(interpreterNode);
         reference.get().registerResolver(resolverNode);

         registered = true;
      }

      ArrayList<ProloGraalBoolean> answers = new ArrayList<>();

      for(ProloGraalQuery request : queries){
         Deque<Integer> currentBranches = new ArrayDeque<>();
         ProloGraalAtom head = new ProloGraalAtom(request.getVariables(),"goals");
         ProloGraalClause goal;
         goal = new ProloGraalClause(request.getVariables());//ProloGraalClauseNodeGen.create(request.getVariables(), new ProloGraalSimpleTermNode(head),goalsArray);
         goal.setHead(head);
         request.getGoals().forEach(proloGraalTerm -> goal.addGoal(proloGraalTerm));
         ProloGraalRuntime runtime;
         runtime = new ProloGraalRuntime(language.getContextReference().get());
         runtime.addProloGraalClause(goal);
         ProloGraalBoolean callResult =
               (ProloGraalBoolean) Truffle.getRuntime()
                     .createCallTarget(reference.get().getResolverNode())
                     .call(runtime, currentBranches, null, false);
         answers.add(callResult);
      }

      if(answers.size()==1)return answers.get(0);

      ProloGraalBooleanList answersList = new ProloGraalBooleanList(answers);

      return answersList;
   }
}
