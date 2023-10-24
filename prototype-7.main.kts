// WRITING BACKEND AND STYLING

@file:DependsOn("org.redundent:kotlin-xml-builder:1.9.0")


import org.redundent.kotlin.xml.Node as XmlNode
import org.redundent.kotlin.xml.xml

// AST framework
abstract class Node(var parent: Node? = null) {
    // todo: next/previous-sibling
    // todo: childrenAndDescendants(), ancestors(), nextSiblings(), previousSiblings()

    val children: ArrayList<Node> = arrayListOf()
    fun addChild(childNode: Node) {
        childNode.parent = this
    }

    abstract fun write(bw: BackendWriter)
}

class Document() : Node(null) {
    override fun write(bw: BackendWriter) {
        bw.write(this)
    }
}

class Header(parent: Node, val level: Int) : Node(parent) {
    override fun write(bw: BackendWriter) {
        bw.write(this)
    }
}

class Paragraph(parent: Node) : Node(parent) {
    override fun write(bw: BackendWriter) {
        bw.write(this)
    }
}

class Text(parent: Node, val text: String) : Node(parent) {
    override fun write(bw: BackendWriter) {
        bw.write(this)
    }
}

interface BackendWriter {
    // pattern visitor
    fun write(h: Header)
    fun write(p: Paragraph)
    fun write(doc: Document)
    fun write(text: Text)
}

// Logic
class OdWriter(val preOdNode: XmlNode, val odtStyle: OdtStyleList) : BackendWriter {
    val process: XmlNode.(Node) -> Unit = { node ->
        // just to simplify further processing
        odtStyle.applyStyle(node, this)
        node.children.forEach {
            it.write(OdWriter(this, odtStyle))
        }
    }

    override fun write(h: Header) {
        preOdNode.apply {
            "text:h" {
                // commented, because cleaner with styles
                // attribute("style:name", "Header ${h.level}")
                process(h)
            }
        }
    }

    override fun write(p: Paragraph) {
        preOdNode.apply {
            "p" {
                process(p)
            }
        }
    }

    override fun write(doc: Document) {
        preOdNode.apply {
            process(doc)
        }
    }

    override fun write(text: Text) {
        preOdNode.apply {
            -text.text
        }
    }
}

// Styling framework
class OdtStyle(
    val given: (node: Node) -> Boolean,
    val style: (node: Node) -> String? = { null },
    val textProperties: (XmlNode.(node: Node) -> Unit)? = null,
    val paragraphProperties: (XmlNode.(node: Node) -> Unit)? = null,

    // each node can potentially output more than one ODT nodes
    val key: String? = null,
)

class OdtStyleList(
    private val style: ArrayList<OdtStyle>
) {
    fun applyStyle(node: Node, xmlNode: XmlNode, key: String? = null) {
        // this way for now))
        style.forEach {
            if (it.given(node)) {
                xmlNode.apply {
                    val style = it.style(node)
                    if (style != null) {
                        attribute("text:style-name", style)
                    }
                }
                xmlNode.apply {
                    if (it.textProperties != null) {
                        val textPropertiesNodeFilter = filter("style:text-properties")
                        val textPropertiesNode =
                            if (textPropertiesNodeFilter.isEmpty()) "style:text-properties" {}
                            else textPropertiesNodeFilter[0]
                        textPropertiesNode.apply {
                            it.textProperties?.invoke(this, node)
                        }
                    }
                }
            }
        }
    }
}

// Logic
val basicOdtStyle = OdtStyleList(
    arrayListOf(
        OdtStyle(
            // if header is encountered then we set style to Header {level}
            // and override font (12 for first level, 11 -- for second and so on)
            { it is Header },
            { "Header ${(it as Header).level}" },
            { attribute("fo:font-size", "${13 - (it as Header).level}pt") }
        ),
        OdtStyle(
            { it is Paragraph },
            { "Body Text" }
        )
    )
)

// Don't talk of June, show me!
val doc = Document().apply {
    children.add(Header(this, 1).apply {
        children.add(Text(this, "Heading 1"))
    })
    children.add(Paragraph(this).apply {
        children.add(Text(this, "Some text"))
    })
    children.add(Header(this, 1).apply {
        children.add(Text(this, "Heading 1"))
    })
}

println(
    OdWriter(
        xml("text:body"), basicOdtStyle
    ).apply { doc.write(this) }
        .preOdNode
)