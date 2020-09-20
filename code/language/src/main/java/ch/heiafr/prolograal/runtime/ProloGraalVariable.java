package ch.heiafr.prolograal.runtime;

import ch.heiafr.prolograal.ProloGraalLanguage;
import ch.heiafr.prolograal.exceptions.ProloGraalTypeCastingError;
import ch.heiafr.prolograal.nodes.ProloGraalProofTreeNode;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

/**
 * Class representing a Prolog variable.
 * @see ProloGraalTerm
 * @see ProloGraalClause
 * @see ProloGraalStructure
 * @author Martin Spoto
 */
@ExportLibrary(InteropLibrary.class)
public final class ProloGraalVariable extends ProloGraalTerm<ProloGraalVariable> {

   // container to save the state of this variable when we use the save/undo methods
   private Deque<ProloGraalVariable> states = new ArrayDeque<>();

   // a suffix for anonymous variables to ensure that they have different internal names
   private static int anonymousVariableSuffix = 0;
   // the name of this variable
   private final String name;
   // the bound status of this variable
   private boolean isBound = false;
   // the bound value of this variable (or null if unbound)
   private ProloGraalTerm<?> boundValue;

   private ProloGraalProofTreeNode observingProofTreeNode;

   /**
    * Creates a new variable, adding a suffix if the name starts with an underscore (anonymous variable)
    * @param variables the variable set of the clause this variable will be contained in.
    *                  If no variable with the same name exists currently in this set, the new variable is added to it.
    * @param name The name of this variable. If it starts with an underscore, this variable is considered as anonymous
    *             and a suffix is automatically added.
    */
   public ProloGraalVariable(Map<ProloGraalVariable, ProloGraalVariable> variables, String name) {
      super(variables);
      if (name.startsWith("_")) {
         name = "_" + anonymousVariableSuffix++;
      }
      this.name = name;
      this.hashCode = name.hashCode() * variables.hashCode();
      variables.putIfAbsent(this, this);
   }

   /**
    * Deep copy constructor
    * @param variables the variable set of the clause this variable will be contained in.
    *                  If no variable with the same name exists currently in this set, the new variable is added to it.
    * @param other the variable that will be copied
    */
   public ProloGraalVariable(Map<ProloGraalVariable, ProloGraalVariable> variables, ProloGraalVariable other) {
      super(variables);
      this.name = other.name;
      this.isBound = other.isBound;
      if (other.isBound) {
         this.boundValue = other.boundValue.copy(variables);
      }
      this.hashCode = name.hashCode() * variables.hashCode();
      variables.putIfAbsent(this, this);
   }

   /**
    * Private shallow copy constructor used to create lightweight copies, to later store them in the state deque.
    * We primarily look to save the bound status and the bound value.
    * @param other the variable that will be used as a base for the light copy.
    */
   @SuppressWarnings("CopyConstructorMissesField")
   private ProloGraalVariable(ProloGraalVariable other) {
      super(null);
      this.name = other.name;
      this.isBound = other.isBound;
      this.boundValue = other.boundValue;
      this.hashCode = other.hashCode;
   }

   public String getName() {
      return name;
   }

   public ProloGraalTerm<?> getBoundValue() {
      return boundValue;
   }

   @Override
   public void observe(ProloGraalProofTreeNode proofTreeNode) {
      observingProofTreeNode = proofTreeNode;
   }

   /**
    * Returns the root value of this variable. The root value is the term that is at the end of the bounding chain.
    * This is either the root value of the term that this variable is currently bound to, or, if this variable is
    * unbound, it is the variable itself.
    * @return The root value of the term that this variable is bound to, or this variable itself if unbound.
    */
   @Override
   public ProloGraalTerm<?> getRootValue() {
      if (this.isBound) {
         if (this.boundValue instanceof ProloGraalVariable) {
            // if we're bound to another variable we need to do that recursively
            return this.boundValue.getRootValue();
         } else {
            return this.boundValue;
         }
      } else {
         return this;
      }
   }

   /**
    * Binds this variable to another term.
    * After this operation, the current variable is bound.
    * @param other The term that this variable should bind to.
    */
   private void bind(ProloGraalTerm<?> other) {
      // recursion check
      if (other instanceof ProloGraalVariable) {
         ProloGraalVariable otherVar = (ProloGraalVariable) other;
         if (otherVar.isBound && otherVar.boundValue == this) {
            return;
         }
      }
      this.isBound = true;
      this.boundValue = other;
      if (observingProofTreeNode != null) observingProofTreeNode.variableListener(this);
      if (ProloGraalLanguage.DEBUG_MODE) {
         System.out.println(name + " = " + other);
      }
   }

   /**
    * Checks if this variable is equal to another. Two variables are considered equals if they have the same name...
    * @param obj The object that will be compared to this variable
    * @return True if the given object is also a variable and that both have the same name.
    */
   @Override
   public boolean equals(Object obj) {
      return obj instanceof ProloGraalVariable && this.name.equals(((ProloGraalVariable) obj).name);
   }

   /**
    * Returns the String representation of this variable, including its root value if it is bound.
    * @return a String representation of this variable, including its root value if it is bound
    */
   @Override
   public String toString() {
      return this.name + (this.isBound ? " = " + this.getRootValue().toString() : "");
   }

   @Override
   public String toGraphString() {
      return "(" + this.name + (this.isBound ? ":" + this.getRootValue().toString() : "") + ")";
   }
   @Override
   public String toGraphStringShort() {
      return this.getRootValue().toString();
   }

   /**
    * Checks whether the given structure contains a reference to this variable, effectively implementing the
    * <a href="https://en.wikipedia.org/wiki/Occurs_check">occurs check</a>.
    * @param root the structure that will be searched for a reference to this variable
    * @return True if the structure contains this variable, false otherwise
    */
   private boolean occursCheck(ProloGraalStructure root) {
      Map<ProloGraalVariable, ProloGraalVariable> subVariables = root.getSubVariables();
      if (subVariables.get(this) == this) { // contains directly this variable
         return true;
      }
      for (ProloGraalVariable var : subVariables.values()) {
         ProloGraalTerm<?> rootValue = var.getRootValue();
         if (rootValue == this) { // or contains indirectly this variable
            return true;
         }
         if (rootValue instanceof ProloGraalStructure) {
            // if any sub variable points to a structure, we need to recursively check that structure as well
            if (occursCheck((ProloGraalStructure) rootValue)) {
               return true;
            }
         }
      }
      return false;
   }

   /**
    * Unifies this variable with another term, and return whether the operation was successful or not.<br>
    * The unification first follows these two rules :
    * <ul>
    *    <li>if the other term is this variable, then the unification always succeeds (a variable can always unify to
    *       itself)</li>
    *    <li>if the other term is a structure that contains this variable, then the unification always fails
    *       (see {@link #occursCheck} for details)</li>
    * </ul>
    * Then it differs depending on whether this variable is already bound or not :
    * <ul>
    *    <li>If this variable is already bound, then we must unify the root value of this variable with the root value
    *       of the other term.</li>
    *    <li>If it is not already bound, then we must bind it to the root value of the other term and the unification
    *       succeeds.</li>
    * </ul>
    * @param other a term to unify with this variable
    * @return True if this variable can be unified with the given term, false otherwise.
    */
   @Override
   public boolean unify(ProloGraalTerm<?> other) {
      // can always unify to itself
      if (other instanceof ProloGraalVariable && other == this) {
         return true;
      }
      // occurs check
      ProloGraalTerm<?> rootValue = other.getRootValue();
      if (rootValue instanceof ProloGraalStructure) {
         if (occursCheck((ProloGraalStructure) rootValue)) {
            return false;
         }
      }

      if (this.isBound) {
         return this.boundValue.getRootValue().unify(rootValue);
      } else {
         this.bind(rootValue);
         return true;
      }
   }

   /**
    * Creates a copy of this variable if it isn't already present in the provided context.
    * If it is already present, return a reference to the already existing variable.
    */
   @Override
   public ProloGraalVariable copy(Map<ProloGraalVariable, ProloGraalVariable> variables) {
      if (variables.containsKey(this)) {
         return variables.get(this);
      } else {
         return new ProloGraalVariable(variables, this);
      }
   }

   public boolean isBound() {
      return isBound;
   }

   /**
    * Saves the state of this variable by creating a shallow copy and storing it
    * (see {@link #ProloGraalVariable(ProloGraalVariable)}).
    * If this variable is bound to another variable, the state of this other variable is also stored.
    */
   public void saveState() {
      //System.out.println("Saving state " + this);
      states.push(new ProloGraalVariable(this));
      if (this.isBound && this.boundValue instanceof ProloGraalVariable) {
         ((ProloGraalVariable) this.boundValue).saveState();
      }
   }

   /**
    * Restores the state of this variable with the most recent saved state.
    * If the variable was previously bound to another variable, the state of this other variable is also restored.
    */
   public void revertState() {
      if(!states.isEmpty()){
         ProloGraalVariable previous = states.pop();
         this.isBound = previous.isBound;
         this.boundValue = previous.boundValue;
         //System.out.println("Restored state " + this);
         if (this.isBound && this.boundValue instanceof ProloGraalVariable) {
            ((ProloGraalVariable) this.boundValue).revertState();
         }
      }
   }

   @Override
   public boolean isVariable() {
      return !isBound;
   }

   @Override
   public boolean isBoundVariable() {
      return isBound;
   }

   @Override
   public boolean isAtom() {
      return isBound && boundValue instanceof ProloGraalAtom;
   }

   @Override
   public boolean isStructure(){
      return isBound && boundValue instanceof ProloGraalStructure;
   }

   @ExportMessage
   @Override
   public boolean isNumber() {
      return isBound && boundValue instanceof ProloGraalNumber;
   }

   public ProloGraalAtom asAtom() throws ProloGraalTypeCastingError {
      if(!isAtom()){
         throw new ProloGraalTypeCastingError(this, ATOM_TYPE);
      }
      return (ProloGraalAtom) boundValue;
   }

   public ProloGraalNumber asNumber() throws ProloGraalTypeCastingError {
      if(!isNumber()){
         throw new ProloGraalTypeCastingError(this, NUMBER_TYPE);
      }
      return (ProloGraalNumber) boundValue;
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
      return (ProloGraalStructure) boundValue;
   }

   @ExportMessage
   public double asDouble() throws UnsupportedMessageException {
      if(isNumber() && fitsInDouble())
         return ((ProloGraalNumber) boundValue).asDouble();
      throw UnsupportedMessageException.create();
   }

   @ExportMessage
   public int asInt() throws UnsupportedMessageException {
      if(isNumber() && fitsInInt())
         return ((ProloGraalNumber) boundValue).asInt();
      throw UnsupportedMessageException.create();
   }

   @ExportMessage
   public long asLong() throws UnsupportedMessageException {
      if(isNumber() && fitsInLong())
         return ((ProloGraalNumber) boundValue).asInt();
      throw UnsupportedMessageException.create();
   }

   @ExportMessage
   public float asFloat() throws UnsupportedMessageException {
      if(isNumber() && fitsInFloat())
         if(fitsInFloat())
         return ((ProloGraalNumber) boundValue).asFloat();
      throw UnsupportedMessageException.create();
   }

   @ExportMessage
   public byte asByte() throws UnsupportedMessageException {
      if(isNumber() && fitsInByte())
         return ((ProloGraalNumber) boundValue).asByte();
      throw UnsupportedMessageException.create();
   }

   @ExportMessage
   public short asShort() throws UnsupportedMessageException {
      if(isNumber() && fitsInShort())
         return ((ProloGraalNumber) boundValue).asShort();
      throw UnsupportedMessageException.create();
   }

   @ExportMessage
   public boolean fitsInByte(){
      if(isNumber())
         return ((ProloGraalNumber) boundValue).fitsInByte();
      return false;
   }

   @ExportMessage
   public boolean fitsInShort(){
      if(isNumber())
         return ((ProloGraalNumber) boundValue).fitsInShort();
      return false;
   }

   @ExportMessage
   public boolean fitsInFloat(){
      if(isNumber())
         return ((ProloGraalNumber) boundValue).fitsInFloat();
      return false;
   }

   @ExportMessage
   public boolean fitsInLong(){
      if(isNumber())
         return ((ProloGraalNumber) boundValue).fitsInLong();
      return false;
   }

   @ExportMessage
   public boolean fitsInInt() {
      if (isNumber())
         return ((ProloGraalNumber) boundValue).fitsInInt();
      return false;
   }

   @ExportMessage
   public boolean fitsInDouble(){
      if(isNumber())
         return ((ProloGraalNumber) boundValue).fitsInDouble();
      return false;
   }

   @ExportMessage
   public boolean isString(){
      return isBound && boundValue instanceof ProloGraalAtom && ((ProloGraalAtom) boundValue).isString();
   }

   @ExportMessage
   public String asString() throws UnsupportedMessageException{
      if(isString()){
         return ((ProloGraalAtom) boundValue).asString();
      }else{
         throw UnsupportedMessageException.create();
      }
   }
}