#!/usr/bin/env python3
"""
Improved XML fixer that handles various XML formatting issues.
"""

import re
import sys
import xml.etree.ElementTree as ET
from xml.dom import minidom

def fix_xml_content(content):
    """
    Fix XML content by removing problematic characters and normalizing structure.
    """
    # Remove all newlines and carriage returns
    content = content.replace('\n', '')
    content = content.replace('\r', '')
    
    # Remove control characters (except tab)
    content = re.sub(r'[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]', '', content)
    
    # Normalize whitespace (multiple spaces become single space)
    content = re.sub(r'\s+', ' ', content)
    
    # Remove spaces between tags and content
    content = re.sub(r'>\s+<', '><', content)
    
    # Remove leading/trailing whitespace
    content = content.strip()
    
    return content

def format_xml_pretty(xml_string):
    """
    Format XML with proper indentation.
    """
    try:
        # Parse the XML
        root = ET.fromstring(xml_string)
        
        # Convert to string with formatting
        rough_string = ET.tostring(root, 'unicode')
        reparsed = minidom.parseString(rough_string)
        
        return reparsed.toprettyxml(indent="  ")
    except ET.ParseError as e:
        print(f"Warning: Could not format XML due to parsing error: {e}")
        return xml_string

def validate_xml(xml_string):
    """
    Validate XML and return True if valid.
    """
    try:
        ET.fromstring(xml_string)
        return True
    except ET.ParseError as e:
        print(f"XML validation error: {e}")
        return False

def main():
    if len(sys.argv) < 2:
        print("Usage: python improved_xml_fix.py <input_file> [--pretty]")
        sys.exit(1)
    
    input_file = sys.argv[1]
    pretty_format = '--pretty' in sys.argv
    
    # Generate output filename
    base_name = input_file.replace('.xml', '')
    output_file = f"{base_name}_fixed.xml"
    
    try:
        # Read the input file
        with open(input_file, 'r', encoding='utf-8') as f:
            content = f.read()
        
        print(f"Original file size: {len(content)} characters")
        
        # Fix the XML content
        fixed_content = fix_xml_content(content)
        
        print(f"Fixed content size: {len(fixed_content)} characters")
        
        # Validate the fixed XML
        if validate_xml(fixed_content):
            print("✓ XML is valid!")
            
            # Format if requested
            if pretty_format:
                try:
                    formatted_content = format_xml_pretty(fixed_content)
                    print("✓ XML formatted with proper indentation")
                except:
                    print("⚠ Could not format XML, using unformatted version")
                    formatted_content = fixed_content
            else:
                formatted_content = fixed_content
            
            # Write the fixed content to output file
            with open(output_file, 'w', encoding='utf-8') as f:
                f.write(formatted_content)
            
            print(f"Fixed XML written to: {output_file}")
            
        else:
            print("⚠ XML still has validation issues")
            # Write anyway in case it's partially fixed
            with open(output_file, 'w', encoding='utf-8') as f:
                f.write(fixed_content)
            print(f"Partially fixed XML written to: {output_file}")
            
    except FileNotFoundError:
        print(f"Error: File '{input_file}' not found.")
        sys.exit(1)
    except Exception as e:
        print(f"Error: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main() 