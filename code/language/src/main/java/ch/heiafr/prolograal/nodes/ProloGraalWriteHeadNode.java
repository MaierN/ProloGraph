package ch.heiafr.prolograal.nodes;

import ch.heiafr.prolograal.runtime.*;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.NodeUtil;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

@NodeInfo(shortName = "ProloGraalWriteHeadNode")
public abstract class ProloGraalWriteHeadNode extends ProloGraalUnaryHeadNode {

    private final ProloGraalContext context;
    private final PrintWriter writer; // used for outputting

    public ProloGraalWriteHeadNode(ProloGraalTerm<?> value, ProloGraalContext context) {
        super(value);
        this.context = context;
        this.writer = new PrintWriter(context.getOutput(), true);
    }

    @Specialization
    @CompilerDirectives.TruffleBoundary
    public ProloGraalBoolean returnValue(ProloGraalTerm arg) {
        boolean is_redo = (Boolean)context.resolverNode.frame.getArguments()[3];
        if (!is_redo) {
            String str = arg.getRootValue().toString();
            if(str.startsWith("'") && str.endsWith("'")) {
                // strip single quotes
                str = str.substring(1, str.length()-1);
            }
            writer.print(str);
            writer.flush();
        }
        return new ProloGraalSuccess();
    }

    @Override
    public ProloGraalBuiltinHeadNode copyBuiltin(Map<ProloGraalVariable, ProloGraalVariable> variables){
        List<Node> childrens = NodeUtil.findNodeChildren(this);
        ProloGraalTermNode child = (ProloGraalTermNode) childrens.get(0);
        return ProloGraalWriteHeadNodeGen.create(value.copy(variables), context, child.copyTermNode(variables));
    }
}
