package com.example.blurr.crawler

import android.graphics.Rect
import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min

/**
 * A utility for finding the best-matching UI element from an XML layout
 * based on a text query and an optional class name.
 */
object ElementFinder {

    private const val TAG = "ElementFinder"

    /**
     * Finds the best-matching element in the given XML.
     * UPDATED: Now accepts an optional `classNameToFind` to resolve ambiguity.
     *
     * @param xmlString The raw XML from the accessibility service.
     * @param textToFind The text to search for (e.g., "New chat").
     * @param classNameToFind (Optional) The specific class name of the element to find.
     * @return The full UIElement if a match is found, otherwise null.
     */
    fun findElement(xmlString: String, textToFind: String, classNameToFind: String? = null): UIElement? {
        val allElements = parseAllElementsFromXml(xmlString)
        if (allElements.isEmpty()) {
            Log.w(TAG, "Could not parse any elements from the provided XML.")
            return null
        }

        // --- THIS IS THE KEY CHANGE ---
        // If a class name is provided, pre-filter the list. Otherwise, use all elements.
        val targetElements = if (!classNameToFind.isNullOrBlank()) {
            allElements.filter { it.class_name == classNameToFind }
        } else {
            allElements
        }

        if (targetElements.isEmpty() && !classNameToFind.isNullOrBlank()) {
            Log.w(TAG, "No elements found with class name: '$classNameToFind'. The element may not be on screen.")
            // Optional: You could fallback to searching allElements here if desired,
            // but for ambiguity resolution, being strict is better.
        }

        val lowerCaseQuery = textToFind.lowercase()

        // Now, run the priority search on the (potentially filtered) list of elements.
        // Priority 1: Exact match on text
        targetElements.firstOrNull { it.text?.lowercase() == lowerCaseQuery }?.let { return it }

        // Priority 2: Exact match on content-description
        targetElements.firstOrNull { it.content_description?.lowercase() == lowerCaseQuery }?.let { return it }

        // Priority 3: "Contains" match on text
        targetElements.firstOrNull { it.text?.lowercase()?.contains(lowerCaseQuery) == true }?.let { return it }

        // Priority 4: "Contains" match on content-description
        targetElements.firstOrNull { it.content_description?.lowercase()?.contains(lowerCaseQuery) == true }?.let { return it }

        Log.w(TAG, "No element found matching query: '$textToFind' ${if(classNameToFind != null) "with class '$classNameToFind'" else ""}")
        return null
    }

    /**
     * A lightweight parser to extract all UI elements from the XML into a simple list.
     */
    private fun parseAllElementsFromXml(xmlString: String): List<UIElement> {
        val elements = mutableListOf<UIElement>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xmlString))

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "node") {
                    val attributes = mutableMapOf<String, String>()
                    for (i in 0 until parser.attributeCount) {
                        attributes[parser.getAttributeName(i)] = parser.getAttributeValue(i)
                    }
                    elements.add(createUIElementFromAttributes(attributes))
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing XML for element finding.", e)
        }
        return elements
    }

    private fun createUIElementFromAttributes(attributes: Map<String, String>): UIElement {
        return UIElement(
            resource_id = attributes["resource-id"],
            text = attributes["text"],
            content_description = attributes["content-desc"],
            class_name = attributes["class"],
            bounds = attributes["bounds"],
            is_clickable = attributes["clickable"]?.toBoolean() ?: false,
            is_long_clickable = attributes["long-clickable"]?.toBoolean() ?: false,
            is_password = attributes["password"]?.toBoolean() ?: false
        )
    }
}
