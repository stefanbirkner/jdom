/*-- 

 $Id: ElementScanner.java,v 1.1 2002/03/08 07:11:24 jhunter Exp $

 Copyright (C) 2001 Brett McLaughlin & Jason Hunter.
 All rights reserved.
 
 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:
 
 1. Redistributions of source code must retain the above copyright
    notice, this list of conditions, and the following disclaimer.
 
 2. Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions, and the disclaimer that follows 
    these conditions in the documentation and/or other materials 
    provided with the distribution.

 3. The name "JDOM" must not be used to endorse or promote products
    derived from this software without prior written permission.  For
    written permission, please contact license@jdom.org.
 
 4. Products derived from this software may not be called "JDOM", nor
    may "JDOM" appear in their name, without prior written permission
    from the JDOM Project Management (pm@jdom.org).
 
 In addition, we request (but do not require) that you include in the 
 end-user documentation provided with the redistribution and/or in the 
 software itself an acknowledgement equivalent to the following:
     "This product includes software developed by the
      JDOM Project (http://www.jdom.org/)."
 Alternatively, the acknowledgment may be graphical using the logos 
 available at http://www.jdom.org/images/logos.

 THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED.  IN NO EVENT SHALL THE JDOM AUTHORS OR THE PROJECT
 CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 SUCH DAMAGE.

 This software consists of voluntary contributions made by many 
 individuals on behalf of the JDOM Project and was originally 
 created by Brett McLaughlin <brett@jdom.org> and 
 Jason Hunter <jhunter@jdom.org>.  For more information on the 
 JDOM Project, please see <http://www.jdom.org/>.
 
 */

package org.jdom.contrib.input.scanner;


import java.io.IOException;
import java.util.*;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.XMLFilterImpl;

import org.jdom.*;
import org.jdom.input.DefaultJDOMFactory;
import org.jdom.input.JDOMFactory;
import org.jdom.input.SAXBuilder;
import org.jdom.input.SAXHandler;

/**
 * An XML filter that uses XPath-like expressions to select the
 * element nodes to build and notifies listeners when these
 * elements becomes available during the parse.
 * <p>
 * ElementScanner does not aim at providing a faster parsing of XML
 * documents.  Its primary focus is to allow the application to
 * control the parse and to consume the XML data while they are
 * being parsed.  ElementScanner can be viewed as a high-level SAX
 * parser that fires events conveying JDOM {@link Element elements}
 * rather that XML tags and character data.</p>
 * <p>
 * ElementScanner only notifies of the parsing of element nodes and
 * does not support reporting the parsing of DOCTYPE data, processing
 * instructions or comments except for those present within the
 * selected elements.  Application needing such data shall register
 * a specific {@link ContentHandler} of this filter to receive them
 * in the form of raw SAX events.</p>
 * <p>
 * To be notified of the parsing of JDOM Elements, an application
 * shall {@link #addElementListener register} objects implementing
 * the {@link ElementListener} interface.  For each registration,
 * an XPath-like expression defines the elements to be parsed and
 * reported.</p>
 * <p>
 * Opposite to XPath, there is no concept of <i>current context</i>
 * or <i>current node</i> in ElementScanner.  And thus, the syntax
 * of the "XPath-like expressions" is not as strict as in XPath and
 * closer to what one uses in XSLT stylesheets in the
 * <code>match</code> specification of the XSL templates:<br>
 * In ElementScanner, the expression "<code>x</code>" matches any
 * element named &quot;x&quot; at any level of the document and not
 * only the root element (as expected in strict XPath if the
 * document is considered the <i>current context</i>).  Thus, in
 * ElementScanner, "<code>x</code>" is equivalent to
 * "<code>//x</code>".</p>
 * <p>
 * Example:
 *  <blockquote><pre>
 *  ElementScanner f = new ElementScanner();
 *
 *  // All descendants of x named y
 *  f.addElementListener(new MyImpl(), "x//y");
 *  // All grandchilden of y named t
 *  f.addElementListener(new MyImpl(), "y/*&thinsp;/t");
 *
 *  ElementListener l2 = new MyImpl2();
 *  f.addElementListener(l2, "/*");     // Root element
 *  f.addElementListener(l2, "z");      // Any node named z
 *
 *  ElementListener l3 = new MyImpl3();
 *  // Any node having an attribute "name" whose value contains ".1"
 *  f.addElementListener(l3, "*[contains(@name,'.1')]");
 *  // Any node named y having at least one "y" descendant
 *  f.addElementListener(l3, "y[.//y]");
 *
 *  f.parse(new InputSource("test.xml"));
 *  </pre></blockquote>
 * </p>
 * <p>
 * The XPath interpreter can be changed (see {@link XPathMatcher}).
 * The default implementation is a mix of the
 * <a href="http://jakarta.apache.org/regexp/index.html">Jakarta
 * RegExp package</a> and the
 * <a href="http://www.jaxen.org">Jaxen XPath interpreter</a>.</p>
 * <p>
 * ElementScanner splits XPath expressions in 2 parts: a node
 * selection pattern and an optional test expression (the part of
 * the XPath between square backets that follow the node selection
 * pattern).</p>
 * <p>
 * Regular expressions are used to match nodes applying the node
 * selection pattern. This allows matching node without requiring to
 * build them (as Jaxen does).<br>
 * If a test expression appears in an XPath expression, Jaxen is used
 * to match the built elements against it and filter out those not
 * matching the test.</p>
 * <p>
 * As a consequence of using regular expressions, the  <i>or</i>"
 * operator ("<code>|</code>" in XPath) is not supported in node
 * selection patterns but can be achieved by registering the same
 * listener several times with different node patterns.</p>
 * <p>
 * <strong>Note</strong>: The methods marked with
 * "<i>[ContentHandler interface support]</i>" below shall not be
 * invoked by the application.  Their usage is reserved to
 * the XML parser.</p>
 *
 * @author Laurent Bihanic
 */
public class ElementScanner extends XMLFilterImpl {

   /**
    * The registered element listeners, each wrapped in a
    * XPathMatcher instance.
    */
   private final        Collection      listeners       = new ArrayList();

   /**
    * The <i>SAXBuilder</i> instance to build the JDOM objects used
    * for parsing the input XML documents.  We actually do not need
    * SAXBuilder per se, we just want to reuse the tons of Java code
    * this class implements!
    */
   private              ParserBuilder   parserBuilder   = new ParserBuilder();

   /**
    * The <i>SAXHandler</i> instance to build the JDOM Elements.
    */
   private              ElementBuilder  elementBuilder  = null;

   /**
    * The path of the being parsed element.
    */
   private              StringBuffer    currentPath     = new StringBuffer();

   /**
    * The matching rules active for the current path.  It includes
    * the matching rules active for all the ancestors of the
    * current node.
    */
   private              Map             activeRules     = new HashMap();

   /**
    * Construct an ElementScanner, with no parent.
    * <p>
    * This filter will have no parent: you must assign a parent
    * before you do any configuration with {@link #setFeature} or
    * {@link #setProperty}.</p>
    * <p>
    * If no parent has been assigned when {@link #parse} is invoked,
    * ElementScanner will use JAXP to get an instance of the default
    * SAX parser installed.</p>
    */
   public ElementScanner() {
      super();
   }

   /**
    * Constructs an ElementScanner with the specified parent.
    */
   public ElementScanner(XMLReader parent) {
      super(parent);
   }

   //-------------------------------------------------------------------------
   // Specific implementation
   //-------------------------------------------------------------------------

   /**
    * Adds a new element listener to the list of listeners
    * maintained by this filter.
    * <p>
    * The same listener can be registered several times using
    * different patterns and several listeners can be registered
    * using the same pattern.</p>
    *
    * @param  listener   the element listener to add.
    * @param  pattern    the XPath expression to select the elements
    *                    the listener is interested in.
    *
    * @throws JDOMException   if <code>listener</code> is null or
    *                         the expression is invalid.
    */
   public void addElementListener(ElementListener listener, String pattern)
                                                        throws JDOMException {
      if (listener != null) {
         this.listeners.add(XPathMatcher.newXPathMatcher(pattern, listener));
      }
      else {
         throw (new JDOMException("Invalid listener object: <null>"));
      }
   }

   /**
    * Removes element listeners from the list of listeners maintained
    * by this filter.
    * <p>
    * if <code>pattern</code> is <code>null</code>, this method
    * removes all registrations of <code>listener</code>, regardless
    * the pattern(s) used for creating the registrations.</p>
    * <p>
    * if <code>listener</code> is <code>null</code>, this method
    * removes all listeners registered for <code>pattern</code>.</p>
    * <p>
    * if both <code>listener</code> and <code>pattern</code> are
    * <code>null</code>, this method performs no action!</p>
    *
    * @param  listener   the element listener to remove.
    */
   public void removeElementListener(ElementListener listener, String pattern) {
      if ((listener != null) || (pattern != null)) {
         for (Iterator i=this.listeners.iterator(); i.hasNext(); ) {
            XPathMatcher m = (XPathMatcher)(i.next());

            if (((m.getListener().equals(listener))  || (listener == null)) &&
                ((m.getExpression().equals(pattern)) || (pattern  == null))) {
               i.remove();
            }
         }
      }
      // Else: Both null => Just ignore that dummy call!
   }

   /**
    * Returns the list of rules that match the element path and
    * attributes.
    *
    * @param  path    the current element path.
    * @param  attrs   the attributes of the element.
    *
    * @return the list of matching rules or <code>null</code> if
    *         no match was found.
    */
   private Collection getMatchingRules(String path, Attributes attrs) {
      Collection matchingRules = null;

      for (Iterator i=this.listeners.iterator(); i.hasNext(); ) {
         XPathMatcher rule = (XPathMatcher)(i.next());

         if (rule.match(path, attrs)) {
            if (matchingRules == null) {
               matchingRules = new ArrayList();
            }
            matchingRules.add(rule);
         }
      }
      return (matchingRules);
   }

   //-------------------------------------------------------------------------
   // SAXBuilder / SAXHandler configuration helper methods
   //-------------------------------------------------------------------------

   /**
    * Sets a custom JDOMFactory for the builder.  Use this to build
    * the tree with your own subclasses of the JDOM classes.
    *
    * @param  factory   <code>JDOMFactory</code> to use.
    */
   public void setFactory(JDOMFactory factory) {
      this.parserBuilder.setFactory(factory);
   }

   /**
    * Activates or desactivates validation for the builder.
    *
    * @param  validate   whether XML validation should occur.
    */
   public void setValidation(boolean validate) {
      this.parserBuilder.setValidation(validate);
   }

   /**
    * Specifies whether or not the parser should elminate whitespace
    * in  element content (sometimes known as "ignorable whitespace")
    * when building the document.  Only whitespace which is contained
    * within element content that has an element only content model
    * will be eliminated (see XML Rec 3.2.1).  For this setting to
    * take effect requires that validation be turned on.
    * <p>
    * The default value is <code>false</code>.</p>
    *
    * @param  ignoringWhite   whether to ignore ignorable whitespace.
    */
   public void setIgnoringElementContentWhitespace(boolean ignoringWhite) {
      this.parserBuilder.setIgnoringElementContentWhitespace(ignoringWhite);
   }

   /**
    * Sets whether or not to expand entities for the builder.
    * <p>
    * A value <code>true</code> means to expand entities as normal
    * content; <code>false</code> means to leave entities unexpanded
    * as <code>EntityRef</code> objects.</p>
    * <p>
    * The default value is <code>true</code>.</p>
    *
    * @param  expand   whether entity expansion should occur.
    */
   public void setExpandEntities(boolean expand) {
      this.parserBuilder.setExpandEntities(expand);
   }

   //-------------------------------------------------------------------------
   // XMLFilterImpl overwritten methods
   //-------------------------------------------------------------------------

   //-------------------------------------------------------------------------
   // XMLReader interface support
   //-------------------------------------------------------------------------

   /**
    * Parses an XML document.
    * <p>
    * The application can use this method to instruct ElementScanner
    * to begin parsing an XML document from any valid input source
    * (a character stream, a byte stream, or a URI).</p>
    * <p>
    * Applications may not invoke this method while a parse is in
    * progress.  Once a parse is complete, an application may reuse
    * the same ElementScanner object, possibly with a different input
    * source.</p>
    * <p>
    * This method is synchronous: it will not return until parsing
    * has ended.  If a client application wants to terminate parsing
    * early, it should throw an exception.</p>
    *
    * @param  source   the input source for the XML document.
    *
    * @throws SAXException   any SAX exception, possibly wrapping
    *                        another exception.
    * @throws IOException    an IO exception from the parser,
    *                        possibly from a byte stream or character
    *                        stream supplied by the application.
    */
   public void parse(InputSource source)  throws IOException, SAXException {
      // Allocate the element builder (SAXHandler subclass).
      this.elementBuilder = this.parserBuilder.getContentHandler();

      // Allocate (if not provided) and configure the parent parser.
      this.setParent(this.parserBuilder.getXMLReader(
                                this.getParent(), this.elementBuilder));

      // And delegate to superclass now that everything has been set-up.
      // Note: super.parse() forces the registration of this filter as
      //       ContentHandler, ErrorHandler, DTDHandler and EntityResolver.
      super.parse(source);
   }

   //-------------------------------------------------------------------------
   // ContentHandler interface support
   //-------------------------------------------------------------------------

   /**
    * <i>[ContentHandler interface support]</i> Receives notification
    * of the beginning of a document.
    *
    * @throws SAXException   any SAX exception, possibly wrapping
    *                        another exception.
    */
   public void startDocument()          throws SAXException {
      // Reset state.
      this.currentPath.setLength(0);
      this.activeRules.clear();

      // Propagate event.
      this.elementBuilder.startDocument();
      super.startDocument();
   }

   /**
    * <i>[ContentHandler interface support]</i> Receives notification
    * of the end of a document.
    *
    * @throws SAXException   any SAX exception, possibly wrapping
    *                        another exception.
    */
   public void endDocument()            throws SAXException {
      // Propagate event.
      this.elementBuilder.endDocument();
      super.endDocument();
   }

   /**
    * <i>[ContentHandler interface support]</i> Begins the scope of
    * a prefix-URI Namespace mapping.
    *
    * @param  prefix   the Namespace prefix being declared.
    * @param  uri      the Namespace URI the prefix is mapped to.
    *
    * @throws SAXException   any SAX exception, possibly wrapping
    *                        another exception.
    */
   public void startPrefixMapping(String prefix, String uri)
                                                        throws SAXException {
      // Propagate event.
      this.elementBuilder.startPrefixMapping(prefix, uri);
      super.startPrefixMapping(prefix, uri);
   }

   /**
    * <i>[ContentHandler interface support]</i> Ends the scope of a
    * prefix-URI Namespace mapping.
    *
    * @param  prefix   the prefix that was being mapped.
    *
    * @throws SAXException   any SAX exception, possibly wrapping
    *                        another exception.
    */
   public void endPrefixMapping(String prefix)          throws SAXException {
      // Propagate event.
      this.elementBuilder.endPrefixMapping(prefix);
      super.endPrefixMapping(prefix);
   }

   /**
    * <i>[ContentHandler interface support]</i> Receives notification
    * of the beginning of an element.
    *
    * @param  uri         the Namespace URI, or the empty string if
    *                     the element has no Namespace URI or if
    *                     Namespace processing is not being performed.
    * @param  localName   the local name (without prefix), or the
    *                     empty string if Namespace processing is
    *                     not being performed.
    * @param  qName       the qualified name (with prefix), or the
    *                     empty string if qualified names are not
    *                     available.
    * @param  attrs       the attributes attached to the element. If
    *                     there are no attributes, it shall be an
    *                     empty Attributes object.
    *
    * @throws SAXException   any SAX exception, possibly wrapping
    *                        another exception.
    */
   public void startElement(String nsUri, String localName,
                            String qName, Attributes attrs)
                                                        throws SAXException {
      // Append new element to the current path.
      this.currentPath.append('/').append(localName);

      // Retrieve the matching rules for this element.
      String eltPath           = this.currentPath.toString();
      Collection matchingRules = this.getMatchingRules(eltPath, attrs);
      if (matchingRules != null) {
         // Matching rules found.
         // => Make them active to trigger element building.
         this.activeRules.put(eltPath, matchingRules);
      }

      // Propagate event.
      if (this.activeRules.size() != 0) {
         this.elementBuilder.startElement(nsUri, localName, qName, attrs);
      }
      super.startElement(nsUri, localName, qName, attrs);
   }

   /**
    * <i>[ContentHandler interface support]</i> Receives notification
    * of the end of an element.
    *
    * @param  uri         the Namespace URI, or the empty string if
    *                     the element has no Namespace URI or if
    *                     Namespace processing is not being performed.
    * @param  localName   the local name (without prefix), or the
    *                     empty string if Namespace processing is
    *                     not being performed.
    * @param  qName       the qualified name (with prefix), or the
    *                     empty string if qualified names are not
    *                     available.
    *
    * @throws SAXException   any SAX exception, possibly wrapping
    *                        another exception.
    */
   public void endElement(String nsUri, String localName, String qName)
                                                        throws SAXException {
      // Get element path.
      String eltPath = this.currentPath.toString();

      // Get the matching rules for this element (if any).
      Collection matchingRules = (Collection)(this.activeRules.remove(eltPath));
      if (matchingRules != null) {
         // Matching rules exist.
         // => Get the last built element
         Element elt = this.elementBuilder.getCurrentElement();

         // And notify all matching listeners
         try {
            for (Iterator i=matchingRules.iterator(); i.hasNext(); ) {
               XPathMatcher matcher = (XPathMatcher)(i.next());

               if (matcher.match(eltPath, elt)) {
                  matcher.getListener().elementMatched(eltPath, elt);
               }
            }
         }
         catch (JDOMException ex1) {
            // Oops! Listener-originated exception.
            // => Fire a SAXException to abort parsing.
            throw (new SAXException(ex1.getMessage(), ex1));
         }
      }
      // Remove notified element from the current path.
      this.currentPath.setLength(
                        this.currentPath.length() - (localName.length() + 1));

      // Propagate event.
      if (this.activeRules.size() != 0) {
         this.elementBuilder.endElement(nsUri, localName, qName);
      }
      super.endElement(nsUri, localName, qName);
   }

   /**
    * <i>[ContentHandler interface support]</i> Receives notification
    * of character data.
    *
    * @param  ch       the characters from the XML document.
    * @param  start    the start position in the array.
    * @param  length   the number of characters to read from the array.
    *
    * @throws SAXException   any SAX exception, possibly wrapping
    *                        another exception.
    */
   public void characters(char[] ch, int start, int length)
                                                        throws SAXException {
      // Propagate event.
      if (this.activeRules.size() != 0) {
         this.elementBuilder.characters(ch, start, length);
      }
      super.characters(ch, start, length);
   }

   /**
    * <i>[ContentHandler interface support]</i> Receives notification
    * of ignorable whitespace in element content.
    *
    * @param  ch       the characters from the XML document.
    * @param  start    the start position in the array.
    * @param  length   the number of characters to read from the array.
    *
    * @throws SAXException   any SAX exception, possibly wrapping
    *                        another exception.
    */
   public void ignorableWhitespace(char[] ch, int start, int length)
                                                        throws SAXException {
      // Propagate event.
      if (this.activeRules.size() != 0) {
         this.elementBuilder.ignorableWhitespace(ch, start, length);
      }
      super.ignorableWhitespace(ch, start, length);
   }

   /**
    * <i>[ContentHandler interface support]</i> Receives notification
    * of processing instruction.
    *
    * @param  target   the processing instruction target.
    * @param  data     the processing instruction data, or
    *                  <code>null</code> if none was supplied.
    *
    * @throws SAXException   any SAX exception, possibly wrapping
    *                        another exception.
    */
   public void processingInstruction(String target, String data)
                                                        throws SAXException {
      // Propagate event.
      if (this.activeRules.size() != 0) {
         this.elementBuilder.processingInstruction(target, data);
      }
      super.processingInstruction(target, data);
   }

   /**
    * <i>[ContentHandler interface support]</i> Receives notification
    * of a skipped entity.
    *
    * @param  name   the name of the skipped entity.
    *
    * @throws SAXException   any SAX exception, possibly wrapping
    *                        another exception.
    */
   public void skippedEntity(String name)               throws SAXException {
      // Propagate event.
      if (this.activeRules.size() != 0) {
         this.elementBuilder.skippedEntity(name);
      }
      super.skippedEntity(name);
   }


   //=========================================================================
   // Deviant implementations of JDOM builder objects
   //=========================================================================

   //-------------------------------------------------------------------------
   // ParserBuilder nested class
   //-------------------------------------------------------------------------

   /**
    * ParserBuilder extends SAXBuilder to provide access to the
    * protected methods of this latter and to allow us using
    * customized SAXHandler and JDOMFactory implementations.
    */
   private static class ParserBuilder extends SAXBuilder {

      public ParserBuilder() {
         super();
      }

      //----------------------------------------------------------------------
      // SAXBuilder overwritten methods
      //----------------------------------------------------------------------

      protected SAXHandler createContentHandler() throws Exception {
         return (new ElementBuilder(new EmptyDocumentFactory(factory)));
      }


      //----------------------------------------------------------------------
      // Specific implementation
      //----------------------------------------------------------------------

      /**
       * Allocates and configures a new XMLReader instance.  If a
       * parser is provided, it will be used; otherwise a new parser
       * will be created.
       *
       * @param  parser    the parser to configure, <code>null</code>
       *                   if one shall be allocated.
       * @param  handler   the SAX ContentHandler to register on the
       *                   parser.
       *
       * @return the configured parser.
       *
       * @throws SAXException   any SAX exception, possibly wrapping
       *                        another exception.
       */
      public XMLReader getXMLReader(XMLReader parser, SAXHandler handler)
                                                        throws SAXException {
         try {
            // Allocate a SAX parser if none was provided.
            if (parser == null) {
               parser = this.createParser();
            }
            // And configure the parser with the ContentHandler.
            this.configureParser(parser, handler);

            return (parser);
         }
         catch (Exception ex1) {
            throw (new SAXException(ex1.getMessage(), ex1));
         }
      }

      /**
       * Allocates and configures a new ElementBuilder object.
       *
       * @return the configured ElementBuilder.
       *
       * @throws SAXException   any SAX exception, possibly wrapping
       *                        another exception.
       */
      public ElementBuilder getContentHandler()         throws SAXException {
         try {
            ElementBuilder handler =
                                (ElementBuilder)(this.createContentHandler());
            this.configureContentHandler(handler);

            return (handler);
         }
         catch (Exception ex1) {
            throw (new SAXException(ex1.getMessage(), ex1));
         }
      }
   }

   //-------------------------------------------------------------------------
   // ElementBuilder nested class
   //-------------------------------------------------------------------------

   /**
    * ElementBuilder extends SAXHandler to support external access
    * to the being-built element.
    */
   private static class ElementBuilder extends SAXHandler {

      /**
       * Creates a new EmptyDocumentFactory wrapping the specified
       * JDOM factory implementation.
       *
       * @param  factory   the JDOM factory to use to create JDOM
       *                   objects.
       *
       * @throws IOException  if thrown by superclass implementation.
       */
      public ElementBuilder(JDOMFactory factory)        throws IOException {
         super(factory);
      }

      //----------------------------------------------------------------------
      // Specific implementation
      //----------------------------------------------------------------------

      /**
       * Returns the object at the top of the Element stack.
       *
       * @return the object at the top of the Element stack.
       */
      public Element getCurrentElement() {
         return ((Element)(this.stack.peek()));
      }
   }

   //-------------------------------------------------------------------------
   // EmptyDocumentFactory nested class
   //-------------------------------------------------------------------------

   /**
    * EmptyDocumentFactory is an implementation of JDOMFactory that
    * wraps an application-provided factory and delegates all
    * method calls to this latter except for document creation calls
    * it intercepts to return instances of EmptyDocument.
    */
   private static class EmptyDocumentFactory implements JDOMFactory {

      /**
       * The wrapped JDOM factory implementation.
       */
      private final JDOMFactory wrapped;

      /**
       * Creates a new EmptyDocumentFactory wrapping the specified
       * JDOM factory implementation.
       *
       * @param  factory   the JDOM factory implementation to wrap
       *                   or <code>null</code> to use the default
       *                   JDOM factory.
       */
      public EmptyDocumentFactory(JDOMFactory factory) {
         this.wrapped = (factory != null)? factory: new DefaultJDOMFactory();
      }

      //----------------------------------------------------------------------
      // Redefined JDOMFactory methods
      //----------------------------------------------------------------------

      public Document document(Element rootElement, DocType docType) {
         return (new EmptyDocument());
      }

      public Document document(Element rootElement) {
         return (new EmptyDocument());
      }

      //----------------------------------------------------------------------
      // Delegated JDOMFactory methods
      //----------------------------------------------------------------------

      public Attribute attribute(String name, String value,
                                              Namespace namespace) {
         return(this.wrapped.attribute(name, value, namespace));
      }
      public Attribute attribute(String name, String value, int type,
                                              Namespace namespace) {
         return(this.wrapped.attribute(name, value, type, namespace));
      }
      public Attribute attribute(String name, String value) {
         return(this.wrapped.attribute(name, value));
      }
      public Attribute attribute(String name, String value, int type) {
         return(this.wrapped.attribute(name, value, type));
      }
      public CDATA cdata(String text) {
         return(this.wrapped.cdata(text));
      }
      public Text text(String text) {
         return(this.wrapped.text(text));
      }
      public Comment comment(String text) {
         return(this.wrapped.comment(text));
      }
      public DocType docType(String elementName,
                             String publicID, String systemID) {
         return(this.wrapped.docType(elementName, publicID, systemID));
      }
      public DocType docType(String elementName, String systemID) {
         return(this.wrapped.docType(elementName, systemID));
      }
      public DocType docType(String elementName) {
         return(this.wrapped.docType(elementName));
      }
      public Element element(String name, Namespace namespace) {
         return(this.wrapped.element(name, namespace));
      }
      public Element element(String name) {
         return(this.wrapped.element(name));
      }
      public Element element(String name, String uri) {
         return(this.wrapped.element(name, uri));
      }
      public Element element(String name, String prefix, String uri) {
         return(this.wrapped.element(name, prefix, uri));
      }
      public ProcessingInstruction processingInstruction(String target,
                                                         Map data) {
         return(this.wrapped.processingInstruction(target, data));
      }
      public ProcessingInstruction processingInstruction(String target,
                                                         String data) {
         return(this.wrapped.processingInstruction(target, data));
      }
      public EntityRef entityRef(String name) {
         return(this.wrapped.entityRef(name));
      }
      public EntityRef entityRef(String name,
                                 String publicID, String systemID) {
         return(this.wrapped.entityRef(name, publicID, systemID));
      }
   }

   //-------------------------------------------------------------------------
   // EmptyDocument nested class
   //-------------------------------------------------------------------------

   /**
    * EmptyDocument extends the standard JDOM Document object to
    * overwrite all methods setting document-level information to
    * ensure the document always remains empty.
    * <p>
    * This implementation is a kludge to prevent SAXHandler to
    * actually build a document with the matched elements as root
    * elements (yes there can be several in our case)!</p>
    */
   private static class EmptyDocument extends Document {

      /**
       * Default constructor.
       */
      public EmptyDocument() {
         super();
      }

      //----------------------------------------------------------------------
      // Document overwritten methods
      //----------------------------------------------------------------------

      public Document setRootElement(Element root)         { return(this); }
      public Document addContent(Comment comment)          { return(this); }
      public Document addContent(ProcessingInstruction pi) { return(this); }
      public Document setContent(List newContent)          { return(this); }
      public Document setDocType(DocType docType)          { return(this); }
   }
}
