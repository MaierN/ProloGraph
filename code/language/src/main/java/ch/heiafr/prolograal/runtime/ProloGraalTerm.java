package ch.heiafr.prolograal.runtime;

import ch.heiafr.prolograal.exceptions.ProloGraalTypeCastingError;
import ch.heiafr.prolograal.nodes.ProloGraalProofTreeNode;

import java.util.Map;

/**
 * Abstract class representing a Prolog term.
 * Defines common methods and facilities.
 * @param <T> Meant to be the type of the child class, so that the {@link #copy(Map)} method returns the type of the
 *           subclass directly. This avoids some type castings when creating copies of terms.
 * @author Martin Spoto
 */
public abstract class ProloGraalTerm<T extends ProloGraalTerm<T>> extends ProloGraalObject {
   public static final String ATOM_TYPE = "atom";
   public static final String VARIABLE_TYPE = "variable";
   public static final String NUMBER_TYPE = "number";
   public static final String STRUCTURE_TYPE = "structure";

   public void observe(ProloGraalProofTreeNode proofTreeNode) {}

   // the hashcode of this object
   // each subclass should set this accordingly in its constructor
   protected int hashCode;

   // set that contains all variables in this term
   // we use a map to be able to retrieve a specific variable (we cannot use a regular Set since we can only check
   // if it contains the element, not access it)
   protected final Map<ProloGraalVariable, ProloGraalVariable> variables;

   protected ProloGraalTerm(Map<ProloGraalVariable, ProloGraalVariable> variables) {
      this.variables = variables;
   }

   // abstract to force subclasses to implement it
   @Override
   public abstract boolean equals(Object obj);

   // subclasses should either override this method or set the provided attribute "hashCode" to a correct value
   @Override
   public int hashCode() {
      return this.hashCode;
   }

   public String toGraphString() {
      return toString();
   }
   public String toGraphStringShort() {
      return toGraphString();
   }

   /**
    * Returns the "root value" of this term. The root value is the term at the end of a possible chain of
    * variables linked to other terms. This method is provided to avoid type casting and
    * facilitate manipulation of heterogeneous terms.<br>
    * The only override of this method is {@link ProloGraalVariable#getRootValue()}, since only variables have special
    * rules for root values, other terms simply return themselves.
    * @return The root value of this term (always itself if the term is not an instance of {@link ProloGraalVariable})
    */
   public ProloGraalTerm<?> getRootValue() {
      return this;
   }

   /**
    * Unifies this term with another one, possibly altering the state of both terms in the process.
    * @param other a term to unify with this one
    * @return True if the twos terms were successfully unified, false otherwise
    */
   public abstract boolean unify(ProloGraalTerm<?> other);

   /**
    * Saves the current state of this term.
    */
   public final void save() {
      variables.values().forEach(ProloGraalVariable::saveState);
   }

   /**
    * Restores the previous state of this term.
    */
   public final void undo() {
      variables.values().forEach(ProloGraalVariable::revertState);
   }

   public Map<ProloGraalVariable, ProloGraalVariable> getVariables() {
      return variables;
   }

   /**
    * Returns a deep copy of this term.
    * @param variables the variable context of the clause that will contain the copy
    * @return A deep copy of this term, having updated the clause's variable list as needed.
    */
   public abstract T copy(Map<ProloGraalVariable, ProloGraalVariable> variables);

   public abstract boolean isAtom();

   public abstract boolean isNumber();

   public abstract boolean isVariable();

   public abstract boolean isBoundVariable();

   public abstract boolean isStructure();

   public ProloGraalAtom asAtom() throws ProloGraalTypeCastingError {
      if(!isAtom()){
         throw new ProloGraalTypeCastingError(this, ATOM_TYPE);
      }
      return (ProloGraalAtom) this;
   }

   public ProloGraalNumber asNumber() throws ProloGraalTypeCastingError {
      if(!isNumber()){
         throw new ProloGraalTypeCastingError(this, NUMBER_TYPE);
      }
      return (ProloGraalNumber) this;
   }

   public ProloGraalVariable asVariable() throws ProloGraalTypeCastingError {
      if(!isVariable()){
         throw new ProloGraalTypeCastingError(this, VARIABLE_TYPE);
      }
      return (ProloGraalVariable) this;
   }

   public ProloGraalStructure asStructure() throws ProloGraalTypeCastingError {
      if(!isStructure()){
         throw new ProloGraalTypeCastingError(this, STRUCTURE_TYPE);
      }
      return (ProloGraalStructure) this;
   }
}