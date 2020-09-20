package ch.heiafr.prolograal.nodes;

import ch.heiafr.prolograal.ProloGraalLanguage;
import ch.heiafr.prolograal.runtime.ProloGraalObject;
import ch.heiafr.prolograal.treegraphs.TreeGraphNode;
import ch.heiafr.prolograal.builtins.predicates.ProloGraalBuiltinClause;
import ch.heiafr.prolograal.exceptions.ProloGraalExistenceError;
import ch.heiafr.prolograal.runtime.*;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Class representing a node of the proof tree.
 * Each node is characterized by its goals.
 * This class is a Truffle executable {@link Node}.
 * @see ProloGraalResolverNode
 * @author Martin Spoto
 */
@NodeInfo(language = ProloGraalLanguage.ID,shortName = "ProofTreeNode")
public class ProloGraalProofTreeNode extends Node {

   private class LocalVariables{
      public ProloGraalTerm<?> currentGoal;
      public int start;
      public List<ProloGraalClause> unifiableClauses;
   }

   private final Map<ProloGraalTerm<?>, List<ProloGraalClause>> clauses; // reference to the context clauses

   //Stacks used to simulate a recursive call with a while loop
   private final Stack<LocalVariables> localVarsStack; // the goals of this node
   private final Stack<Deque<ProloGraalTerm<?>>> goalsStack; // the goals of this node

   private final boolean traceFlag; // is the trace ON ?

   private ArrayList<String> toStringArray(ArrayList<ProloGraalVariable> list) {
      ArrayList<String> r = new ArrayList<>();
      for (ProloGraalVariable var : list) {
         r.add(var.toGraphString());
      }
      return r;
   }

   private ArrayList<String> toStringArray(Deque<ProloGraalTerm<?>> list) {
      ArrayList<String> r = new ArrayList<>();
      for (ProloGraalTerm<?> var : list) {
         r.add(var.toGraphString());
      }
      return r;
   }

   private final ArrayList<ProloGraalVariable> listenedVariables = new ArrayList<>();
   
   public TreeGraphNode tree;

   private enum ExecuteState {
      BEGIN,
      RUNNING,
      RETURN
   }

   public static final String TRACE_CALL_TEXT = "Call: ";
   public static final String TRACE_EXIT_TEXT = "Exit: ";
   public static final String TRACE_LEFT_WHITE_SPACE = "\t";
   public static final String TRACE_BETWEEN_WHITE_SPACE = " ";
   public static final String TRACE_QUESTION_MARK = "?";

   public ProloGraalProofTreeNode(Map<ProloGraalTerm<?>, List<ProloGraalClause>> clauses,
                                  Deque<ProloGraalTerm<?>> goals, boolean traceFlag, TreeGraphNode tree) {
      this.clauses = clauses;
      this.traceFlag = traceFlag;
      this.localVarsStack = new Stack<>();
      this.goalsStack = new Stack<>();
      goalsStack.push(goals);
      this.tree = tree;
   }

   public void variableListener(ProloGraalVariable var) {
      listenedVariables.add(var);
   }

   /**
    * Execute this node, creating a new child node containing the current goal list with its first goal replaced by
    * its body.
    * @param branches in-out parameter containing a list of branches.
    *                 Used to determine the starting point of this node when descending,
    *                 and to store the path to next possible branch when climbing back up the call stack after
    *                 a success is found.
    * @throws ProloGraalExistenceError if the context contains no clauses compatible with the first goal
    * @return either {@link ProloGraalSuccess} or {@link ProloGraalFailure}, depending on whether this proof tree
    * branch ends in a success (empty node) or not
    */
   public ProloGraalObject execute(VirtualFrame frame, Deque<Integer> branches) throws ProloGraalExistenceError {
      ExecuteState executeState = ExecuteState.BEGIN;
      ProloGraalObject returnedValue = null;

      //using a while loop to simulate a recursive call without using the java stack
      while(!goalsStack.isEmpty()){

         //switch case used to jump where we stopped if we simulated a new call
         //executeState enum is manipulated through the code to simulate this behaviour
         switch (executeState) {
            case BEGIN:
               executeState = ExecuteState.RUNNING;
               Deque<ProloGraalTerm<?>> currentGoals = goalsStack.pop();
               if (ProloGraalLanguage.DEBUG_MODE) {
                  System.out.println("ProofTreeNode : " + currentGoals);
               }

               int start = 0;
               boolean skipTreeNode = !branches.isEmpty();
               if (!branches.isEmpty()) {
                  // if we're redoing we need to skip to the right branch directly
                  start = branches.pop();
               }

               if (currentGoals.isEmpty()) { // leaf node
                  returnedValue = new ProloGraalSuccess();
                  executeState = ExecuteState.RETURN;
                  break;
               }

               ProloGraalTerm<?> currentGoal = currentGoals.peek();

               if (traceFlag) {
                  System.out.println(TRACE_LEFT_WHITE_SPACE + goalsStack.size() + 1 + TRACE_LEFT_WHITE_SPACE + (start + 1)
                        + TRACE_BETWEEN_WHITE_SPACE + TRACE_CALL_TEXT + currentGoal.toString()
                        + TRACE_BETWEEN_WHITE_SPACE + TRACE_QUESTION_MARK);
               }

               // get the list of all possible clauses based on the name of the predicate
               List<ProloGraalClause> possibleClauses = clauses.get(currentGoal);
               if (possibleClauses == null || possibleClauses.isEmpty()) // if no match, throw an error
               {
                  throw new ProloGraalExistenceError(currentGoal);
               }

               //System.out.println("==== goals: " + currentGoals);
               if (tree != null) {
                  if (!skipTreeNode) {
                     TreeGraphNode newTree = new TreeGraphNode();
                     newTree.parent = tree;
                     newTree.text = toStringArray(currentGoals);
                     newTree.linkText = toStringArray(listenedVariables);
                     tree.children.add(newTree);
                     tree = newTree;
                  } else {
                     TreeGraphNode newTree = tree;
                     while (newTree.parent != null) newTree = newTree.parent;
                     //tree = tree.children.get(start - 1);
                     tree = tree.children.get(tree.children.size() - 1);
                  }
               }

               // copy used for lambda
               //ProloGraalTerm<?> currentGoalCopy = currentGoal.copy(currentGoal.getVariables());
               // filter clauses that are unifiable with the current goal, creating copies and saving variables state as needed
               List<ProloGraalClause> unifiableClauses =
                     IntStream.range(0, possibleClauses.size())
                           .filter(x -> {
                              //ProloGraalClause clause = possibleClauses.get(x).copy();
                              //currentGoalCopy.save();
                              //boolean r = clause.getHead().unify(currentGoalCopy);
                              //currentGoalCopy.undo();
                              //return r;
                              return true;
                           })
                           .mapToObj(x -> possibleClauses.get(x).copy()) // create a copy of each filtered clauses
                           .collect(Collectors.toList());

               //we push everything into their stacks because
               //we need to be sure that those variables are initialized with the right value
               //when accessing directly to the RETURN case.
               LocalVariables localVariables = new LocalVariables();
               localVariables.currentGoal = currentGoal;
               localVariables.unifiableClauses = unifiableClauses;
               localVariables.start = start;
               localVarsStack.push(localVariables);
               goalsStack.push(currentGoals);
               //we don't break since we want to continue the standard behaviour of the ProofTreeNode
            case RETURN:
               //retrieve local variables from their stacks
               localVariables = localVarsStack.pop();
               currentGoal = localVariables.currentGoal;
               unifiableClauses = localVariables.unifiableClauses;
               start = localVariables.start;
               currentGoals = goalsStack.pop();
               for (int i = start; i < unifiableClauses.size(); i++) {
                  if(executeState!=ExecuteState.RETURN) {
                     // no need to copy here since it is already one
                     ProloGraalClause unifiableClause = unifiableClauses.get(i);

                     ProloGraalTerm<?> currentGoalCopy = currentGoal.copy(currentGoal.getVariables());
                     ProloGraalClause clause = unifiableClause.copy();
                     currentGoalCopy.save();
                     boolean r = clause.getHead().unify(currentGoalCopy);
                     currentGoalCopy.undo();

                     if (!r) {
                        //System.out.println("=== --------");
                        if (tree != null) {
                           TreeGraphNode newTree = new TreeGraphNode();
                           newTree.parent = tree;
                           newTree.isEmpty = true;
                           tree.children.add(newTree);
                        }
                        continue;
                     }

                     currentGoal.save();

                     listenedVariables.clear();
                     currentGoal.observe(this);
                     unifiableClause.getHead().observe(this);

                     // unify the head with the current goal
                     unifiableClause.getHead().unify(currentGoal);
                     if (ProloGraalLanguage.DEBUG_MODE) {
                        System.out.println("Unified " + currentGoal + " with " + unifiableClause.getHead());
                     }
                     //System.out.println("=== new bound variables: " + listenedVariables);

                     // only execute built-in the first time we traverse their nodes
                     if (branches.isEmpty()) {
                        if(currentGoal instanceof ProloGraalBuiltinStructure){
                           if(!((ProloGraalBuiltinStructure)currentGoal).getBuiltin().executeBuiltin(frame).asBoolean()) {
                              returnedValue = new ProloGraalFailure();
                              executeState = ExecuteState.RETURN;
                              break;
                           }
                        }else if (unifiableClause instanceof ProloGraalBuiltinClause) {
                           // if the clause is a built-in, execute its internal behaviour
                           if (!((ProloGraalBuiltinClause) unifiableClause).executeBuiltin(frame).asBoolean()) {
                              //if the builtin don't provide a success, return a failure and break the switch case
                              returnedValue = new ProloGraalFailure();
                              executeState = ExecuteState.RETURN;
                              break;
                           }
                        }
                     }

                     // create a copy of the current goals
                     Deque<ProloGraalTerm<?>> newGoals = new ArrayDeque<>(currentGoals);

                     List<ProloGraalTerm<?>> body = unifiableClause.getGoals();

                     // always remove the first goal since it will be replaced
                     newGoals.poll();

                     // no need for distinction between facts and regular clauses
                     // add all the new goals
                     Collections.reverse(body);

                     body.forEach(newGoals::addFirst);

                     //push i because when we will pop it, we want to continue the for loop from where it stopped.
                     localVariables.start = i;
                     localVariables.currentGoal = currentGoal;
                     localVariables.unifiableClauses = unifiableClauses;
                     localVarsStack.push(localVariables);
                     goalsStack.push(currentGoals);
                     //goalsStack.push(newGoals);
                     goalsStack.push(newGoals);
                     executeState = ExecuteState.BEGIN;
                     break;
                  }
                  executeState = ExecuteState.RUNNING;
                  ProloGraalObject result = returnedValue;
                  if (result instanceof ProloGraalBoolean && ((ProloGraalBoolean) result).asBoolean()) {
                     // skip last possible nodes if empty, or always add if we already have added a path
                     if (!branches.isEmpty() || i + 1 < unifiableClauses.size()) {
                        if (branches.isEmpty()) {
                           i = i + 1; // if we're at the bottom go directly to next node
                        }
                        branches.push(i); // add the path that gave the success
                     }
                     if (traceFlag) {
                        System.out.println(TRACE_LEFT_WHITE_SPACE + goalsStack.size() + 1 + TRACE_LEFT_WHITE_SPACE + (start + 1)
                              + TRACE_BETWEEN_WHITE_SPACE + TRACE_EXIT_TEXT + currentGoal + TRACE_BETWEEN_WHITE_SPACE + TRACE_QUESTION_MARK);
                     }

                     //System.out.println("=== (succes)");
                     if (tree != null) {
                        tree = tree.parent;
                     }

                     //set the returned value as a success and break the for loop to simulate a return statement
                     returnedValue = new ProloGraalSuccess();
                     executeState = ExecuteState.RETURN;
                     break;
                  }
                  // undo all changes that the unification may have done
                  currentGoal.undo();
               }
               //if we broke the for to return something or simulate another call,
               //break before we set returnedValue to failure (behaviour of no unifiable clause)
               if(executeState == ExecuteState.BEGIN || executeState == ExecuteState.RETURN)break;

               if (tree != null) {
                  tree = tree.parent;
               }

               returnedValue = new ProloGraalFailure();
               executeState = ExecuteState.RETURN;
               break;
         }
      }
      /*
      try{
         File file = File.createTempFile("SVMHeapDump-", ".hprof");
         VMRuntime.dumpHeap(file.getAbsolutePath(), true);
      }catch(Exception e){
         e.printStackTrace();
      }*/

      //System.out.println(((ProloGraalBoolean) returnedValue).asBoolean() ? "=== SUCCES" : "=== ECHEC");
      if (tree != null && ((ProloGraalBoolean) returnedValue).asBoolean()) {
         TreeGraphNode newTree = tree;
         while (newTree.children.size() > 0 && !newTree.children.get(newTree.children.size() - 1).isEmpty) {
            newTree = newTree.children.get(newTree.children.size() - 1);
         }
         TreeGraphNode successTreeNode = new TreeGraphNode();
         successTreeNode.linkText = toStringArray(listenedVariables);
         successTreeNode.text = new ArrayList<>();
         successTreeNode.subText = "SUCCES";
         successTreeNode.parent = newTree;
         newTree.children.add(successTreeNode);
      }

      return returnedValue;
   }
}
