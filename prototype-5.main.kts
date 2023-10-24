// PARSING

@file:DependsOn("org.redundent:kotlin-xml-builder:1.9.0")
@file:DependsOn("com.vladsch.flexmark:flexmark-all:0.64.8")

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.nodes.Node as JsoupNode
import kotlin.reflect.KFunction0

// Model

open class BasicSourceNode()

// Model (simplified)
abstract class Node {
    var sourceNode: BasicSourceNode? = null
    val children: ArrayList<Node> = arrayListOf()
}

class Document() : Node()
class Header(val level: Int) : Node()
class Paragraph() : Node()
class Text(val text: String) : Node()


// Jsoup is wrapped to allow javascript implementation
class HtmlNode(private val jsoupNode: JsoupNode) : BasicSourceNode() {
    constructor(doc: String) : this(Jsoup.parse(doc))

    fun isText(): Boolean {
        return if (jsoupNode is TextNode) true else false
    }


    fun nodeName(): String {
        return jsoupNode.nodeName()
    }

    fun nodeNameOrText(): String {
        return if (jsoupNode is Element) jsoupNode.nodeName() else jsoupNode.toString()
    }

    fun nodeText(): String? {
        return if (jsoupNode is TextNode) jsoupNode.toString() else null
    }

    fun selectAtXpath(xpath: String): HtmlNode? {
        return if (jsoupNode is Element && jsoupNode.selectXpath(xpath)[0] != null)
            HtmlNode(jsoupNode.selectXpath(xpath)[0])
        else null
    }

    fun firstChild(): HtmlNode? {
        val firstChild = jsoupNode.firstChild()
        return if (firstChild == null) null else HtmlNode(firstChild)
    }

    fun nextSibling(): HtmlNode? {
        val nextSibling = jsoupNode.nextSibling()
        return if (nextSibling == null) null else HtmlNode(nextSibling)
    }

    // Node can be matched just by xpath (most common situation)
    // If matched returns this node and the sequential node

    fun matchByXpath(xpath: String): Pair<HtmlNode?, HtmlNode?> {
        val parentJsoupNode =
            if (jsoupNode.parentNode() is Element) jsoupNode.parentNode() as Element
            else return (Pair(null, null))
        val matchedNode =
            if (parentJsoupNode.selectXpath(xpath).contains(this.jsoupNode)
                || (xpath == "text()" && jsoupNode is TextNode)
            ) this
            else null
        val nextJsoupNodeSibling = matchedNode?.jsoupNode?.nextSibling()
        val nextHtmlNodeSibling =
            if (nextJsoupNodeSibling != null) HtmlNode(nextJsoupNodeSibling) else null
        return Pair(matchedNode, nextHtmlNodeSibling)
    }
}

// Base class for reading from HTML
open class HtmlReader(private val node: Node) {
    private val htmlNode: HtmlNode
    fun node(): Node {
        return node
    }

    init {
        // AST Node doesn't always reference source node, but reader node should
        val nodeSourceNode = node.sourceNode
        if (nodeSourceNode is HtmlNode) {
            htmlNode = nodeSourceNode
        } else {
            throw Exception("${this::class.java.simpleName} expects HtmlNode as a source")
        }
    }

    private var cursorNode: HtmlNode? = htmlNode.firstChild()

    fun addToAST(newNode : Node, confirmedNode : HtmlNode): Node {
        newNode.sourceNode = confirmedNode
        node().children.add(newNode)
        return newNode
    }

    protected fun tryStep(kFunction0: () -> Unit) {
        if (cursorNode != null) {
            kFunction0.invoke()
        }
    }

    // iterating detection functions
    protected fun detectBy(vararg kFunctions: KFunction0<Unit>) {
        while (cursorNode != null) {
            val stepOldCursorNode = cursorNode as HtmlNode
            run step@{
                kFunctions.forEach {
                    val oldCursorNode = cursorNode
                    it.invoke()
                    if (oldCursorNode != cursorNode) {
                        return@step
                    }
                }
            }
            if (stepOldCursorNode == cursorNode) {
                cursorNode = stepOldCursorNode.nextSibling()
            }
        }
    }

    // detecting by xpath entry point
    fun detectByXpath(xpath: String, initNode: (confirmedNode: HtmlNode) -> Unit) {
        if (cursorNode == null) {
           return
        }
        val oldCursorNode = cursorNode as HtmlNode
        val (confirmedNode, nextNode) =
            oldCursorNode.matchByXpath(xpath)
        if (confirmedNode != null) {
            if (confirmedNode.nodeNameOrText() != "") {
                val newNode = initNode.invoke(confirmedNode)
            }
            cursorNode = nextNode
        }
    }


}

// Logic
class FlexMarkupReader(node: Node) : HtmlReader(node) {

    fun detectAll() {
        detectBy(
            ::detectH,
            ::detectP,
            ::detectText,
        )
    }

    private fun detectH() {
        detectByXpath("h1|h2|h3|h4|h5|h6") { confirmedNode ->
            val level = "(?<=h)[0-9]".toRegex()
                .matchEntire(confirmedNode.nodeName())?.value ?: "0"
            val newHeader = Header(level.toInt())
            FlexMarkupReader(addToAST(newHeader, confirmedNode)).detectAll()
        }
    }

    private fun detectP() {
        detectByXpath("p") {confirmedNode ->
            FlexMarkupReader(addToAST(Paragraph(), confirmedNode)).detectAll()
        }
    }

    private fun detectText() {
        detectByXpath("text()") { confirmedNode ->
            val text = Text(confirmedNode.nodeText() ?: "")
            val newTextNode = Text(text.text)
            addToAST(newTextNode, confirmedNode)
        }
    }
}


// Please don't expl'ine, Show me!
var md = """
    # Heading 1
    
    Some text
    
    ## Heading 1.1""".trimIndent()

val options = MutableDataSet();
val parser = Parser.builder(options).build()
val renderer = HtmlRenderer.builder(options).build()
val mdDocument = parser.parse(md)
val html = renderer.render(mdDocument)
val htmlNode = HtmlNode(html).selectAtXpath("/html/body")
val document = Document().apply { sourceNode = htmlNode }
val reader = FlexMarkupReader(document).apply { detectAll() }

// Output AST
fun outputNode(node: Node, level: Int) {
    if (node is Text) {
        println("${" ".repeat(level * 2)}text: ${node.text}")
    } else {
        println(
            "${" ".repeat(level * 2)}${node::class.java.simpleName}"
        )
        node.children.forEach {
            outputNode(it, level + 1)
        }
    }
}

println(html)
outputNode(document, 0)