package ch.heiafr.prolograal.treegraphs;

import java.util.ArrayList;
import java.util.StringJoiner;

public class TreeGraphNode {
    public static String NEW_LINE = "_NL_";
    public TreeGraphNode parent;
    public ArrayList<TreeGraphNode> children = new ArrayList<>();
    public ArrayList<String> text;
    public ArrayList<String> linkText;
    public String subText;
    public boolean isEmpty;

    public String toString() {
        String r = "{";
        if (isEmpty) {
            r += "\"is_empty\": true, ";
        } else {
            if (subText != null) {
                r += "\"sub_text\": \"" + subText + "\", ";
            }
            if (text != null) {
                if (text.size() > 0) {
                    r += "\"text\": [";
                    StringJoiner sj = new StringJoiner(", ");
                    for (String s : text) {
                        sj.add("\"" + s + "\"");
                    }
                    r += sj;
                    r += "], ";
                }
            }
            if (linkText != null && linkText.size() > 0) {
                r += "\"link_text\": [";
                StringJoiner sj = new StringJoiner(", ");
                for (String s : linkText) {
                    sj.add("\"" + s + "\"");
                }
                r += sj;
                r += "], ";
            }
            if (subText == null) {
                r += "\"children\": [";
                StringJoiner sj = new StringJoiner(", ");
                for (TreeGraphNode child : children) {
                    sj.add(child.toString());
                }
                r += sj;
                r += "], ";
            }
        }
        r = r.substring(0, r.length() - 2);
        r += "}";

        return r;
    }
}
