#!/usr/bin/env python3
"""
Script to fix invalid XML files that have newlines breaking XML attributes and elements.
"""

import re
import sys
import xml.etree.ElementTree as ET
from xml.dom import minidom

def fix_xml_content(content):
    """
    Fix XML content by removing newlines that break XML structure.
    """
    # Remove newlines that break XML attributes
    # This regex finds newlines that are not between XML tags
    content = re.sub(r'\n(?![^<]*>)', '', content)
    
    # Remove extra whitespace between attributes
    content = re.sub(r'\s+', ' ', content)
    
    # Clean up any remaining issues
    content = content.strip()
    
    return content

def format_xml(xml_string):
    """
    Format XML string with proper indentation.
    """
    try:
        # Parse the XML
        root = ET.fromstring(xml_string)
        
        # Convert to string with formatting
        rough_string = ET.tostring(root, 'unicode')
        reparsed = minidom.parseString(rough_string)
        
        return reparsed.toprettyxml(indent="  ")
    except ET.ParseError as e:
        print(f"Error parsing XML: {e}")
        return xml_string

def main():
    if len(sys.argv) != 2:
        print("Usage: python fix_xml.py <input_file>")
        sys.exit(1)
    
    input_file = sys.argv[1]
    output_file = input_file.replace('.xml', '_fixed.xml')
    
    try:
        # Read the input file
        with open(input_file, 'r', encoding='utf-8') as f:
            content = f.read()
        
        print(f"Original file size: {len(content)} characters")
        
        # Fix the XML content
        fixed_content = fix_xml_content(content)
        
        print(f"Fixed content size: {len(fixed_content)} characters")
        
        # Try to format the XML
        try:
            formatted_content = format_xml(fixed_content)
        except:
            # If formatting fails, use the fixed content as is
            formatted_content = fixed_content
        
        # Write the fixed content to output file
        with open(output_file, 'w', encoding='utf-8') as f:
            f.write(formatted_content)
        
        print(f"Fixed XML written to: {output_file}")
        
        # Validate the XML
        try:
            ET.parse(output_file)
            print("✓ XML is valid!")
        except ET.ParseError as e:
            print(f"⚠ XML still has issues: {e}")
            
    except FileNotFoundError:
        print(f"Error: File '{input_file}' not found.")
        sys.exit(1)
    except Exception as e:
        print(f"Error: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main() 