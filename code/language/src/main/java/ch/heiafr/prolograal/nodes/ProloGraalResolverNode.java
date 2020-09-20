package ch.heiafr.prolograal.nodes;

import ch.heiafr.prolograal.ProloGraalLanguage;
import ch.heiafr.prolograal.runtime.ProloGraalObject;
import ch.heiafr.prolograal.treegraphs.TreeGraphNode;
import ch.heiafr.prolograal.runtime.*;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.RootNode;

import java.util.*;

/**
 * Class handling the start and end of a resolution.
 * This class extends {@link RootNode} as it is a starting point in the execution process (a root of the AST).
 * @see ProloGraalInterpreterNode
 * @see ProloGraalProofTreeNode
 * @author Martin Spoto
 */
@NodeInfo(language = "Prolog", description = "Root node that spawns a proof tree")
public final class ProloGraalResolverNode extends RootNode {

   private final ProloGraalContext context; // the context
   private final Map<ProloGraalTerm<?>, List<ProloGraalClause>> clauses; // reference to the context clauses

   public ProloGraalResolverNode(ProloGraalLanguage language) {
      super(language);
      this.context = language.getContextReference().get();
      this.clauses = context.getRuntime().getClauses();
   }

   public VirtualFrame frame;

   @Override
   public Object execute(VirtualFrame frame) {
      this.frame = frame;
      // we get the goal runtime and the current branches from the frame arguments, passed by the interpreter
      ProloGraalRuntime goalRuntime = (ProloGraalRuntime) frame.getArguments()[0];
      @SuppressWarnings("unchecked")
      Deque<Integer> branches = (Deque<Integer>) frame.getArguments()[1];
      TreeGraphNode treeGraphNode = (TreeGraphNode) frame.getArguments()[2];

      if (ProloGraalLanguage.DEBUG_MODE) {
         System.out.println("Executing : " + goalRuntime.getFirstClause());
         System.out.println("Branches : " + branches);
      }

      // copy the first clause of the goal (there should be only one) to prevent unwanted side effects
      ProloGraalClause clause = goalRuntime.getFirstClause().copy();
      // get the goals of the clause as a deque
      // a deque is used to be able to efficiently remove / add at the start of the collection
      // it is possible to use a stack by reversing everything but doing it like that is more logical
      // also, the javadoc for the Stack encourages the use of a Deque instead
      Deque<ProloGraalTerm<?>> goals = new ArrayDeque<>(clause.getGoals());
      // create the root node of the proof tree
      ProloGraalProofTreeNode proofTreeNode = new ProloGraalProofTreeNode(clauses, goals, context.isTraceFlag(), treeGraphNode);
      // start the execution of the proof tree, and stores the result
      ProloGraalObject r = proofTreeNode.execute(frame, branches);
      if(r instanceof ProloGraalBoolean){
         // if the result is a success, we need to store the variables state and pass them in a ProloGraalSuccess
         // to display them later in the interpreter
         if (((ProloGraalBoolean)r).asBoolean()) {
            r = new ProloGraalSuccess(clause.getVariables());
         }
      }

      
      return r;
   }
}