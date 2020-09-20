package ch.heiafr.prolograal.runtime;

import ch.heiafr.prolograal.builtins.ProloGraalBuiltinAtoms;
import ch.heiafr.prolograal.nodes.ProloGraalProofTreeNode;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Class representing a Prolog structure, or composed term.
 * @see ProloGraalList
 * @author Martin Spoto
 */
@ExportLibrary(InteropLibrary.class)
public class ProloGraalStructure extends ProloGraalTerm<ProloGraalStructure> {
   // the functor of this structure
   protected ProloGraalAtom functor;
   // the sub terms of this structure
   protected final List<ProloGraalTerm<?>> subterms;
   // Variables contained in this structure only (children only)
   protected final Map<ProloGraalVariable, ProloGraalVariable> subVariables;
   // arity of this structure
   protected int arity;

   public ProloGraalStructure(Map<ProloGraalVariable, ProloGraalVariable> variables) {
      super(variables);
      this.subterms = new ArrayList<>();
      this.subVariables = new HashMap<>();
   }

   protected ProloGraalStructure(ProloGraalStructure copiedStruct){
      super(copiedStruct.getVariables());
      functor = copiedStruct.functor;
      subterms = copiedStruct.subterms;
      subVariables = copiedStruct.subVariables;
      arity = copiedStruct.arity;
   }
   
   @Override
   public void observe(ProloGraalProofTreeNode proofTreeNode) {
      for (int i = 0; i < this.arity; i++) {
         this.subterms.get(i).observe(proofTreeNode);
      }
   }

   /**
    * Add a subterm to this structure.
    * In case the subterm is a variable or a structure, correctly adds it to the various variable lists.
    * @param subterm the subterm to add
    */
   public void addSubterm(ProloGraalTerm<?> subterm) {
      this.subterms.add(subterm);
      if (subterm instanceof ProloGraalVariable) {
         // the variable is a direct child of this structure, we need to add it to both the sub variables
         // and the global (term) variables.
         this.subVariables.put((ProloGraalVariable) subterm, (ProloGraalVariable) subterm);
         this.variables.put((ProloGraalVariable) subterm, (ProloGraalVariable) subterm);
      }
      if (subterm instanceof ProloGraalStructure) {
         // if the subterm is also a structure, we need to add all subvariables from this structure to our own list
         this.subVariables.putAll(((ProloGraalStructure) subterm).subVariables);
      }
      arity++;
   }

   public Map<ProloGraalVariable, ProloGraalVariable> getVariables() {
      return this.variables;
   }

   public Map<ProloGraalVariable, ProloGraalVariable> getSubVariables() {
      return this.subVariables;
   }

   public void setFunctor(ProloGraalAtom functor) {
      this.functor = functor;
   }

   public ProloGraalAtom getFunctor() {
      return this.functor;
   }

   public List<ProloGraalTerm<?>> getArguments() {
      return this.subterms;
   }

   public int getArity() {
      return this.arity;
   }

   /**
    * Creates a "pretty" representation of this term, by replacing variables with their values and correctly displaying
    * lists. Must be used as the last operation, after resolving, or may have unwanted side effects.
    * @return A pretty string representation of this structure.
    */
   public String toRootString() {
      if (functor == null) {
         return "(Empty structure)";
      } else if (functor.equals(ProloGraalBuiltinAtoms.DOT_OPERATOR) && arity == 2) {
         ProloGraalList list = ProloGraalList.fromInternal(this);
         String tail = !list.getTail().getRootValue().equals(ProloGraalBuiltinAtoms.EMPTY_LIST) ?
                 " | " + list.getTail().getRootValue().toString() : "";
         return "[" + list.getItems().stream().map(ProloGraalTerm::getRootValue).map(x -> {
            if (x instanceof ProloGraalStructure) {
               return ((ProloGraalStructure) x).toRootString();
            } else {
               return x.toString();
            }
         }).collect(Collectors.joining(", ")) + tail + "]";
      }
      String subtermsString = subterms.stream().map(ProloGraalTerm::getRootValue).map(x -> {
         if (x instanceof ProloGraalStructure) {
            return ((ProloGraalStructure) x).toRootString();
         } else {
            return x.toString();
         }
      }).collect(Collectors.joining(", "));
      return functor.getName() + "/" + arity + "(" + subtermsString + ")";
   }

   /**
    * Dumb string representation, but still with pretty printing for lists. Do not alter the state of this structure,
    * so may be safely used at any point for debugging or displaying purposes.
    * @return A string representation of this structure.
    */
   @Override
   public String toString() {
      if (functor == null) {
         return "(Empty structure)";
      } else if (functor.equals(ProloGraalBuiltinAtoms.DOT_OPERATOR) && arity == 2) {
         ProloGraalList list = ProloGraalList.fromInternal(this);
         String tail = !list.getTail().equals(ProloGraalBuiltinAtoms.EMPTY_LIST) ? " | " + list.getTail().toString()
                 : "";
         return "[" + list.getItems().stream().map(Object::toString).collect(Collectors.joining(", ")) + tail + "]";
      }
      String subtermsString = subterms.toString();
      return functor.getName() + "/" + arity + "(" + subtermsString.substring(1, subtermsString.length() - 1) + ")";
   }

   @Override
   public String toGraphString() {
      if (functor == null) {
         return "(Empty structure)";
      } else if (functor.equals(ProloGraalBuiltinAtoms.DOT_OPERATOR) && arity == 2) {
         ProloGraalList list = ProloGraalList.fromInternal(this);
         String tail = !list.getTail().equals(ProloGraalBuiltinAtoms.EMPTY_LIST) ? " | " + list.getTail().toString()
                 : "";
         return "[" + list.getItems().stream().map(Object::toString).collect(Collectors.joining(", ")) + tail + "]";
      }
      String subtermsString = subterms.toString();
      return functor.getName() + "(" + subtermsString.substring(1, subtermsString.length() - 1) + ")";
   }

   /**
    * Check that two structure are considered equals.
    * @param obj to compare
    * @return True if the two structure have the same arity and the same functor (the arguments can be different),
    *         false otherwise
    */
   @Override
   public boolean equals(Object obj) {
      if (obj == null) {
         return false;
      }
      if (!(obj instanceof ProloGraalStructure)) {
         return false;
      }
      ProloGraalStructure other = (ProloGraalStructure) obj;

      return this.functor.equals(other.functor) && this.arity == other.arity;
   }

   /**
    * Compute the hashcode of this structure.
    * This function is important for the map in {@link ProloGraalRuntime}.<br>
    * We use the arity to compute the hash as well to be able to select the right predicate quicker when resolving.
    * @return a hashcode composed of the hash of the functor + the arity of this structure
    */
   @Override
   public int hashCode() {
      return functor.hashCode * Integer.hashCode(arity) /*+ subterms.get(0).hashCode()*/;
   }

   /**
    * Unify this structure with another term, possibly bounding the variables of this structure to be able to unify.
    * @param other The term to unify with this structure
    * @return True if the other term is a structure with the same arity, and that each of its arguments can be unified
    *         with its direct correspondent in the other structure, or if the other term is either an unbound variable
    *         or a variable bound to a unifiable structure.
    */
   @Override
   public boolean unify(ProloGraalTerm<?> other) {
      if (this.equals(other)) {
         for (int i = 0; i < this.arity; i++) {
            if (!this.subterms.get(i).getRootValue().unify(((ProloGraalStructure) other).subterms.get(i))) {
               return false;
            }
         }
         return true;
      } else if (other instanceof ProloGraalVariable) {
         // if other is a variable, we delegate the unification process to it.
         return other.unify(this);
      }
      return false;
   }

   /**
    * Creates a deep copy of this structure.
    * @param variables the variable set from the clause in which this structure will be contained
    * @return A deep copy of this structure, having no link to the original object. The variables parameter is updated
    *         accordingly with the possible sub variables of this structure.
    */
   @Override
   public ProloGraalStructure copy(Map<ProloGraalVariable, ProloGraalVariable> variables) {
      ProloGraalStructure struct = new ProloGraalStructure(variables);
      struct.setFunctor(this.functor.copy(variables));
      this.subterms.forEach(x -> struct.addSubterm(x.copy(variables)));
      return struct;
   }

   @Override
   public boolean isVariable() {
      return false;
   }

   @Override
   public boolean isBoundVariable() {
      return false;
   }

   @Override
   public boolean isAtom() {
      return false;
   }

   @Override
   public boolean isStructure(){
      return true;
   }

   @ExportMessage
   @Override
   public boolean isNumber() {
      return false;
   }
   @ExportMessage boolean fitsInByte() { return false; }
   @ExportMessage boolean fitsInShort() { return false; }
   @ExportMessage boolean fitsInInt() { return false; }
   @ExportMessage boolean fitsInLong() { return false; }
   @ExportMessage boolean fitsInFloat() { return false; }
   @ExportMessage boolean fitsInDouble() { return false; }
   @ExportMessage byte asByte() throws UnsupportedMessageException { return (byte) 0; }
   @ExportMessage short asShort() throws UnsupportedMessageException { return (short) 0; }
   @ExportMessage int asInt() throws UnsupportedMessageException { return 0; }
   @ExportMessage long asLong() throws UnsupportedMessageException { return 0L; }
   @ExportMessage float asFloat() throws UnsupportedMessageException { return 0.0F; }
   @ExportMessage double asDouble() throws UnsupportedMessageException { return 0.0D; }
}