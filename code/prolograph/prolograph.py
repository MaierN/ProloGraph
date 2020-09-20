
from xml.dom import minidom as dom
import subprocess
import sys
import json
import base64

CHAR_WIDTH = 7.5
NODE_X_OFFSET = 30
NODE_Y_OFFSET = 100
EMPTY_X_OFFSET = 20

def init_doc():
    doc = dom.Document()

    mxGraphModel = doc.createElement('mxGraphModel')
    doc.appendChild(mxGraphModel)
    mxGraphModel.setAttribute('grid', '0')
    mxGraphModel.setAttribute('page', '0')

    root = doc.createElement('root')
    mxGraphModel.appendChild(root)

    mxCell_0 = doc.createElement('mxCell')
    root.appendChild(mxCell_0)
    mxCell_0.setAttribute('id', '0')

    mxCell_1 = doc.createElement('mxCell')
    root.appendChild(mxCell_1)
    mxCell_1.setAttribute('id', '1')
    mxCell_1.setAttribute('parent', '0')

    return doc, root

def render_tree(doc, root, tree, parent_id, next_id, level, level_width, parent_pos_x, parent_pos_y):
    if len(level_width) <= level:
        level_width.append(0)
    this_id = next_id
    if 'is_empty' not in tree:
        mxCell = doc.createElement('mxCell')
        root.appendChild(mxCell)
        mxCell.setAttribute('id', str(next_id))
        mxCell.setAttribute('value', tree['text'])
        mxCell.setAttribute('vertex', '1')
        mxCell.setAttribute('parent', '1')
        mxCell.setAttribute('style', 'html=1;fontFamily=Lucida Console;')

        this_width = (len(tree['text'].replace('<u>','').replace('</u>',''))+1) * CHAR_WIDTH

        mxGeometry = doc.createElement('mxGeometry')
        mxCell.appendChild(mxGeometry)
        mxGeometry.setAttribute('x', str(level_width[level]))
        mxGeometry.setAttribute('y', str(level * NODE_Y_OFFSET))
        mxGeometry.setAttribute('width', str(this_width))
        mxGeometry.setAttribute('height', '20')
        mxGeometry.setAttribute('as', 'geometry')

        this_pos_x = level_width[level] + this_width/2
        this_pos_y = level * NODE_Y_OFFSET + 20/2

        level_width[level] += this_width + NODE_X_OFFSET

        next_id += 1

        if 'sub_text' in tree:
            mxCell = doc.createElement('mxCell')
            root.appendChild(mxCell)
            mxCell.setAttribute('id', str(next_id))
            mxCell.setAttribute('value', tree['sub_text'])
            mxCell.setAttribute('vertex', '1')
            mxCell.setAttribute('parent', '1')
            mxCell.setAttribute('style', 'html=1;fontFamily=Lucida Console;strokeColor=none;')

            this_sub_width = (len(tree['text'])+1) * CHAR_WIDTH

            mxGeometry = doc.createElement('mxGeometry')
            mxCell.appendChild(mxGeometry)
            mxGeometry.setAttribute('x', str(level_width[level] - this_width - NODE_X_OFFSET))
            mxGeometry.setAttribute('y', str(level * NODE_Y_OFFSET + 20))
            mxGeometry.setAttribute('width', str(this_sub_width))
            mxGeometry.setAttribute('height', '20')
            mxGeometry.setAttribute('as', 'geometry')

            next_id += 1

        if 'link_text' in tree:
            mxCell = doc.createElement('mxCell')
            root.appendChild(mxCell)
            mxCell.setAttribute('id', str(next_id))
            mxCell.setAttribute('value', tree['link_text'])
            mxCell.setAttribute('edge', '1')
            mxCell.setAttribute('parent', '1')
            mxCell.setAttribute('source', str(parent_id))
            mxCell.setAttribute('target', str(this_id))
            mxCell.setAttribute('style', 'endArrow=none;fontSize=10;')

            mxGeometry = doc.createElement('mxGeometry')
            mxCell.appendChild(mxGeometry)
            mxGeometry.setAttribute('as', 'geometry')
            
            next_id += 1


        if 'children' in tree:
            for child in tree['children']:
                next_id = render_tree(doc, root, child, this_id, next_id, level+1, level_width, this_pos_x, this_pos_y)
        
    else:
        mxCell = doc.createElement('mxCell')
        root.appendChild(mxCell)
        mxCell.setAttribute('id', str(this_id))
        mxCell.setAttribute('edge', '1')
        mxCell.setAttribute('parent', '1')
        mxCell.setAttribute('source', str(parent_id))
        mxCell.setAttribute('style', 'endArrow=none;dashed=1;')

        mxGeometry = doc.createElement('mxGeometry')
        mxCell.appendChild(mxGeometry)
        mxGeometry.setAttribute('as', 'geometry')

        this_x = level_width[level]
        this_y = level * NODE_Y_OFFSET

        mxPoint = doc.createElement('mxPoint')
        mxGeometry.appendChild(mxPoint)
        mxPoint.setAttribute('x', str(this_x - (this_x - parent_pos_x)/2))
        mxPoint.setAttribute('y', str(this_y - (this_y - parent_pos_y)/2))
        mxPoint.setAttribute('as', 'targetPoint')
        
        level_width[level] += EMPTY_X_OFFSET + NODE_X_OFFSET
        
        next_id += 1

    return next_id

def correct_tree(tree):
    if 'text' not in tree:
        tree['text'] = '   '
    else:
        s = ''
        first = True
        for t in tree['text']:
            if first:
                s += '<u>'
            else:
                s += ', '
            s += t
            if first:
                s += '</u>'
            first = False
        tree['text'] = s + ' ?'
    
    if 'link_text' in tree:
        s = ''
        first = True
        odd = False
        for t in tree['link_text']:
            if not first:
                if False and odd:
                    s += ' '
                else:
                    s += '_NL_'
            s += t
            first = False
            odd = not odd
        tree['link_text'] = s
    
    if 'children' in tree:
        for child in tree['children']:
            correct_tree(child)
    else:
        tree['children'] = []

def deflate_base64(text):
    raw_data = text.encode('utf-8')
    data = bytearray()

    MAX_BLOCK_SIZE = 255

    while len(raw_data) > MAX_BLOCK_SIZE:
        new_data = bytearray(5)
        new_data[0] = 0x00
        new_data[1] = 0xff
        new_data[2] = 0x00
        new_data[3] = new_data[1] ^ 0xff
        new_data[4] = new_data[2] ^ 0xff

        data += new_data
        data += raw_data[:MAX_BLOCK_SIZE]
        raw_data = raw_data[MAX_BLOCK_SIZE:]

    new_data = bytearray(5)
    new_data[0] = 0b10000000
    new_data[1] = len(raw_data)
    new_data[2] = 0x00
    new_data[3] = new_data[1] ^ 0xff
    new_data[4] = new_data[2] ^ 0xff

    data += new_data
    data += raw_data

    return base64.b64encode(data)

def save_doc(doc, path, path_drawio):
    xml_str = doc.toprettyxml('  ').replace('_NL_', '&#xa;')
    '''
    f = open(path,"w")
    f.write(xml_str)
    f.close()
    '''

    f = open(path_drawio,'wb')
    f.write('<mxfile><diagram name="Page-1">'.encode('utf-8'))
    f.write(deflate_base64(xml_str))
    f.write('</diagram></mxfile>'.encode('utf-8'))
    f.close()

    print("Saved successfully!")

if __name__ == "__main__":

    if len(sys.argv) < 3:
        print("usage:")
        print(sys.argv[0], "<input_file> <request>")
        exit()

    command = ["java", "-Dtruffle.class.path.append=../language/target/classes", "-Dfile.encoding=UTF-8", "-cp", "../launcher/target/classes", "ch.heiafr.prolograal.launcher.ProloGraalMain", sys.argv[1]]
    prolograal = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.PIPE)
    out, err = prolograal.communicate(input=("graph.\nautoredo.\n" + sys.argv[2] + "\nexit.\n").encode('utf-8'))
    data = out.decode('utf-8')
    tree = ""
    lines = data.split('\n')
    for i in range(len(lines)):
        if lines[i] == '============ tree graph:' and i+1 < len(lines):
            tree = lines[i+1]

    #print("Using tree:", tree)

    tree = json.loads(tree)['children'][0]
    correct_tree(tree)

    #print("Corrected tree:", tree)

    print('...')

    # First one to get the level offsets
    doc, root = init_doc()
    level_width = []
    render_tree(doc, root, tree, 1, 2, 0, level_width, 0, 0)
    max_width = max(level_width)
    for i in range(len(level_width)):
        level_width[i] = (max_width - level_width[i]) / 2

    # Real one
    doc, root = init_doc()
    render_tree(doc, root, tree, 1, 2, 0, level_width, 0, 0)

    save_doc(doc, 'out.xml', 'out.drawio')
