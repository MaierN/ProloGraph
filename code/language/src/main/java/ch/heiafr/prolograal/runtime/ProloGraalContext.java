package ch.heiafr.prolograal.runtime;

import ch.heiafr.prolograal.ProloGraalLanguage;
import ch.heiafr.prolograal.nodes.ProloGraalEvalRootNode;
import ch.heiafr.prolograal.nodes.ProloGraalInterpreterNode;
import ch.heiafr.prolograal.nodes.ProloGraalResolverNode;
import com.oracle.truffle.api.TruffleLanguage;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Class holding the general context of the application, with the main nodes and runtime, as well as input/output. <br>
 * The access to the instance is handled by the {@link ProloGraalLanguage#getContextReference()} method.
 * @see ProloGraalEvalRootNode
 * @see ProloGraalLanguage
 * @author Martin Spoto
 */
public class ProloGraalContext {

   // the main runtime containing the parsed clauses
   private ProloGraalRuntime runtime;
   // the interpreter node
   private ProloGraalInterpreterNode interpreterNode;
   // the resolver node
   public ProloGraalResolverNode resolverNode;

   // input and output streams that we get from the environment
   private final InputStream input;
   private final OutputStream output;

   // trace option (log the proof tree path)
   private boolean traceFlag;

   public ProloGraalContext(TruffleLanguage.Env env) {
      this.input = env.in();
      this.output = env.out();
      traceFlag = false;
   }

   public InputStream getInput() {
      return input;
   }

   public OutputStream getOutput() {
      return output;
   }

   public void registerRuntime(ProloGraalRuntime runtime) {
      this.runtime = runtime;
   }

   public void registerInterpreter(ProloGraalInterpreterNode interpreterNode) {
      this.interpreterNode = interpreterNode;
   }

   public void registerResolver(ProloGraalResolverNode resolverNode) {
      this.resolverNode = resolverNode;
   }

   public ProloGraalRuntime getRuntime() {
      return runtime;
   }

   public ProloGraalInterpreterNode getInterpreterNode() {
      return interpreterNode;
   }

   public ProloGraalResolverNode getResolverNode() {
      return resolverNode;
   }

   public boolean isTraceFlag() { return traceFlag; }

   public void setTraceFlag(boolean traceFlag) { this.traceFlag = traceFlag; }
}