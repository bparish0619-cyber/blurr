package com.example.blurr.crawler

import android.content.Context
import android.graphics.Rect
import android.util.Log
import com.google.gson.GsonBuilder
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min


/**
 * A sophisticated parser that simplifies the raw UI XML into a clean,
 * semantically meaningful list of UI elements. It merges descriptive child nodes
 * into their interactive parents and then prunes nested interactive elements.
 */
class SemanticParser(private  val applicationContext: Context) {

    // Internal data class to represent the XML tree structure.
    private data class XmlNode(
        val attributes: MutableMap<String, String> = mutableMapOf(),
        var parent: XmlNode? = null,
        val children: MutableList<XmlNode> = mutableListOf(),
        // --- Fields for merging logic ---
        var mergedText: MutableList<String> = mutableListOf(),
        var isSubsumed: Boolean = false
    ) {
        // Helper to get an attribute
        fun get(key: String): String? = attributes[key]
        fun getBool(key: String): Boolean = attributes[key]?.toBoolean() ?: false
    }

    /**
     * Parses the raw XML hierarchy and returns a simplified, semantically merged JSON string.
     *
     * @param xmlString The raw XML content from the accessibility service.
     * @param screenWidth The physical width of the device screen.
     * @param screenHeight The physical height of the device screen.
     * @return A JSON string representing a list of clean UIElements.
     */
    fun parse(xmlString: String, screenWidth: Int, screenHeight: Int): String {
        // Step 1: Build the complete tree from the XML.
        val rootNode = buildTreeFromXml(xmlString)

        // Step 2: Perform the semantic merge (upward text propagation).
        if (rootNode != null) {
            mergeDescriptiveChildren(rootNode)
        }

        // Step 3: Flatten the tree to a preliminary list of important elements.
        val preliminaryElements = mutableListOf<UIElement>()
        if (rootNode != null) {
            flattenAndFilter(rootNode, preliminaryElements, screenWidth, screenHeight)
        }

        visualizeCurrentScreen(applicationContext, preliminaryElements )

        // Step 5: Serialize the final list to a pretty JSON string.
        val gson = GsonBuilder().setPrettyPrinting().create()
        return gson.toJson(preliminaryElements)
    }


    fun visualizeCurrentScreen(context: Context, elements: List<UIElement>) {
        // Create an instance of the drawer
        val overlayDrawer = DebugOverlayDrawer(context)

        // Call the function to draw the boxes. They will disappear automatically.
        overlayDrawer.drawLabeledBoxes(elements)
    }
    /**
     * Traverses the XML and builds a tree of XmlNode objects, preserving the hierarchy.
     */
    private fun buildTreeFromXml(xmlString: String): XmlNode? {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xmlString))

        var root: XmlNode? = null
        var currentNode: XmlNode? = null
        val nodeStack = ArrayDeque<XmlNode>()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "node") {
                        val newNode = XmlNode()
                        for (i in 0 until parser.attributeCount) {
                            newNode.attributes[parser.getAttributeName(i)] = parser.getAttributeValue(i)
                        }

                        if (root == null) {
                            root = newNode
                            currentNode = newNode
                        } else {
                            currentNode?.children?.add(newNode)
                            newNode.parent = currentNode
                        }
                        nodeStack.addLast(newNode)
                        currentNode = newNode
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "node") {
                        nodeStack.removeLastOrNull()
                        currentNode = nodeStack.lastOrNull()
                    }
                }
            }
            eventType = parser.next()
        }
        return root
    }

    /**
     * Performs a depth-first traversal. When it finds a descriptive but non-clickable node,
     * it walks up the tree to find a clickable ancestor and "donates" its text to it.
     */
    private fun mergeDescriptiveChildren(node: XmlNode) {
        // First, recurse to the deepest children.
        for (child in node.children) {
            mergeDescriptiveChildren(child)
        }

        // Now, process the current node.
        val text = node.get("text")
        val contentDesc = node.get("content-desc")
        val hasDescriptiveText = !text.isNullOrBlank() || !contentDesc.isNullOrBlank()

        if (hasDescriptiveText && !node.getBool("clickable")) {
            // This node is descriptive but not clickable. Find an interactive ancestor.
            var ancestor = node.parent
            while (ancestor != null) {
                if (ancestor.getBool("clickable")) {
                    // Found a clickable ancestor. Donate text and mark this node for pruning.
                    val description = if (!text.isNullOrBlank()) text else contentDesc!!
                    ancestor.mergedText.add(description)
                    node.isSubsumed = true
                    break // Stop the upward walk
                }
                ancestor = ancestor.parent
            }
        }
    }

    /**
     * Traverses the merged tree and creates the final, flat list of UIElement objects,
     * filtering out subsumed and unimportant nodes.
     */
    private fun flattenAndFilter(node: XmlNode, finalElements: MutableList<UIElement>, screenWidth: Int, screenHeight: Int) {
        // A node is considered important if it's clickable OR has text that wasn't merged away.
        val isImportant = node.getBool("clickable") ||
                (!node.get("text").isNullOrBlank() && !node.isSubsumed) ||
                (!node.get("content-desc").isNullOrBlank() && !node.isSubsumed)

        if (isImportant && !node.isSubsumed) {
            // Add a check to ensure the element is within the visible screen bounds.
            val bounds = parseBounds(node.get("bounds"))
            Log.d("DIM", "Bounds: $bounds, ${node.get("content-desc")}")
            if (bounds != null &&
//                [2160,690][1080,878]
               bounds.left >= 0 && bounds.right <= screenWidth && // Horizontal check
                bounds.top >= 0 && bounds.bottom <= screenHeight  && // Vertical check
                bounds.left != bounds.right && // none zero widht
                bounds.top != bounds.bottom  // none zero hieght
            ) {
                // Combine original text with any text merged from children.
                val combinedText = mutableListOf<String>()
                node.get("text")?.takeIf { it.isNotBlank() }?.let { combinedText.add(it) }
                combinedText.addAll(node.mergedText)

                finalElements.add(
                    UIElement(
                        resource_id = node.get("resource-id"),
                        text = combinedText.joinToString(" | "), // Join merged texts
                        content_description = node.get("content-desc"),
                        class_name = node.get("class"),
                        bounds = node.get("bounds"),
                        is_clickable = node.getBool("clickable"),
                        is_long_clickable = node.getBool("long-clickable"),
                        is_password = node.getBool("password")
                    )
                )
            }
        }

        // Continue traversal.
        for (child in node.children) {
            flattenAndFilter(child, finalElements, screenWidth, screenHeight)
        }
    }

    /**
     * Helper to parse a bounds string "[x1,y1][x2,y2]" into an Android Rect object.
     * Now includes validation to ensure the rectangle is well-formed.
     */
    private fun parseBounds(boundsString: String?): Rect? {
        if (boundsString == null) return null
        val pattern = Pattern.compile("\\[(-?\\d+),(-?\\d+)\\]\\[(-?\\d+),(-?\\d+)\\]")
        val matcher = pattern.matcher(boundsString)
        return if (matcher.matches()) {
            try {
                val left = matcher.group(1).toInt()
                val top = matcher.group(2).toInt()
                val right = matcher.group(3).toInt()
                val bottom = matcher.group(4).toInt()

                // NEW: Fix reversed coordinates by taking the min and max of the values.
                val fixedLeft = min(left, right)
                val fixedTop = min(top, bottom)
                val fixedRight = max(left, right)
                val fixedBottom = max(top, bottom)

                Rect(fixedLeft, fixedTop, fixedRight, fixedBottom)
            } catch (e: NumberFormatException) {
                // Handle cases where parsing to Int fails
                null
            }
        } else {
            null
        }
    }
}
