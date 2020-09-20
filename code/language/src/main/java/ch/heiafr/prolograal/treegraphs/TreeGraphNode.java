package ch.heiafr.prolograal.treegraphs;

import java.util.ArrayList;

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
                    for (String s : text) {
                        r += "\"" + s + "\", ";
                    }
                    r = r.substring(0, r.length() - 2);
                    r += "], ";
                }
            }
            if (linkText != null && linkText.size() > 0) {
                r += "\"link_text\": [";
                for (String s : linkText) {
                    r += "\"" + s + "\", ";
                }
                r = r.substring(0, r.length() - 2);
                r += "], ";
            }
            if (subText == null) {
                r += "\"children\": [";
                for (TreeGraphNode child : children) {
                    r += child.toString() + ", ";
                }
                r = r.substring(0, r.length() - 2);
                r += "], ";
            }
        }
        r = r.substring(0, r.length() - 2);
        r += "}";

        return r;
    }
}
