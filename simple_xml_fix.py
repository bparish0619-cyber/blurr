#!/usr/bin/env python3
"""
Simple XML fixer that removes newlines without parsing the XML.
This is more robust for severely malformed XML files.
"""

import re
import sys

def fix_xml_simple(content):
    """
    Simple approach: remove all newlines and normalize whitespace.
    """
    # Remove all newlines
    content = content.replace('\n', '')
    content = content.replace('\r', '')
    
    # Normalize whitespace (multiple spaces become single space)
    content = re.sub(r'\s+', ' ', content)
    
    # Remove spaces between tags and content
    content = re.sub(r'>\s+<', '><', content)
    
    # Remove leading/trailing whitespace
    content = content.strip()
    
    return content

def main():
    if len(sys.argv) != 2:
        print("Usage: python simple_xml_fix.py <input_file>")
        sys.exit(1)
    
    input_file = sys.argv[1]
    output_file = input_file.replace('.xml', '_fixed.xml')
    
    try:
        # Read the input file
        with open(input_file, 'r', encoding='utf-8') as f:
            content = f.read()
        
        print(f"Original file size: {len(content)} characters")
        
        # Fix the XML content
        fixed_content = fix_xml_simple(content)
        
        print(f"Fixed content size: {len(fixed_content)} characters")
        
        # Write the fixed content to output file
        with open(output_file, 'w', encoding='utf-8') as f:
            f.write(fixed_content)
        
        print(f"Fixed XML written to: {output_file}")
        
        # Basic validation - check if it starts and ends properly
        if fixed_content.startswith('<?xml') and fixed_content.endswith('</hierarchy>'):
            print("✓ XML structure looks correct!")
        else:
            print("⚠ XML structure may have issues")
            
    except FileNotFoundError:
        print(f"Error: File '{input_file}' not found.")
        sys.exit(1)
    except Exception as e:
        print(f"Error: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main() 