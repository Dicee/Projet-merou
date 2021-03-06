/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * 
 *	Author(s) :
 *	  - Zohour Abouakil
 *    - David Courtinot
 */

package cfg

import scala.reflect.runtime.universe
import ast._
import ast.model._
import ctl._

/**
 * This file contains the model we are going to use to link the AST classes with the CTL ones.
 * @author Zohour Abouakil
 * @author David Courtinot
 */

case class CFGMetaVar(name: String) extends MetaVariable {
    override def hashCode       = name.hashCode
    override def toString       = name

    override def equals(a: Any) = a match {
        case CFGMetaVar(value) => value == name
        case _                 => false 
    }
}

sealed abstract class CFGVal extends Value

/**
 * CFGExpr represents any expression that can be found or extracted by an ExprPattern on a CFG node
 */
final case class CFGExpr(expr: Expr) extends CFGVal {
    override def toString = expr.toString
}
object CFGExpr extends TypeOf[CFGVal] {
	override def cast(n: CFGVal) = n match { case CFGExpr(_) => true; case _ => false }    
}

/**
 * CFGDecl represents a C++ declaration. The equality between two CFGDecl is based on their ID in
 * the AST.
 */
final case class CFGDecl(id: String, typeOf: String, name: String) extends CFGVal {
    override val hashCode        = id.hashCode
    override def equals(a: Any)  = a match { case CFGDecl(id,_,_) => id == this.id; case _ => false }
}
object CFGDecl extends TypeOf[CFGVal] { override def cast(n: CFGVal) = n match { case CFGDecl(_,_,_) => true; case _ => false } }

/**
 * CFGDef, just like CFGDecl represents a C++ declaration. However, while CFGDecl corresponds to an
 * actual declaration, CFGDef only represents the semantic of a declaration and not the declaration
 * itself. Indeed, two CFGDef are considered equal if they declare a variable of the same name and 
 * the same type.
 */
final case class CFGDef(typeOf: String, name: String) extends CFGVal 
object CFGDef extends TypeOf[CFGVal] { override def cast(n: CFGVal) = n match { case CFGDef(_,_) => true; case _ => false } }

/**
 * CFGString represents any string that can be matched by a StringPattern (operator symbol, type name...).
 */
final case class CFGString(s: String) extends CFGVal 
object CFGString extends TypeOf[CFGVal] { override def cast(n: CFGVal) = n match { case CFGString(_) => true; case _ => false } }

/**
 * ConvertNodes contains various methods enabling to fetch expressions contained by a CFGNode of any kind.
 */
object ConvertNodes {
	private def getAllVal   (expr: Expr): Set[CFGVal] = getAllExpr(expr) ++ getAllString(expr)
	// this method is called recursively on the sub-expressions as the FindExprLabelizer may extract any of the sub-expressions
    private def getAllExpr  (expr: Expr): Set[CFGVal] = expr.getSubExprs.flatMap(getAllExpr(_)).toSet + CFGExpr(expr)
    private def getAllString(expr: Expr): Set[CFGVal] = expr match {
		case BinaryOp        (_,_,_,op    ) => Set(op)
		case CompoundAssignOp(_,_,_,op    ) => Set(op)
		case UnaryOp         (_,_,op    ,_) => Set(op)
		case CallExpr        (typeOf,ref,_) => Set(typeOf,ref.targetName)
		case CXXNewExpr      (typeOf    ,_) => Set(typeOf)
		case _                              => Set()
	}
    
    /**
     * Returns the single expression contained by a node, if any.
     */
    def getExpr(p: ProgramNode): Option[Expr] = p match {
        case If        (expr,_,_)                        => Some(expr)
        case While     (expr,_,_)                        => Some(expr)
        case Expression(expr,_,_)                        => Some(expr)
        case Switch    (expr,_,_)                        => Some(expr)
        case For       (Some(expr),_,_)                  => Some(expr)
        // see comment in the convert method
        case Statement (VarDecl(name,typeOf,expr),cr,id) => expr.map(BinaryOp(typeOf,SourceCodeNode(DeclRefExpr(typeOf,name,id),cr,id),_,"="))
        case _                                           => None
    }
    
    /**
     * Returns a conversion function from ProgramNode to the Set[CFGVal] likely to be extracted
     * by Pattern(s) matching
     */
    def convert: (ProgramNode => Set[CFGVal]) = (p: ProgramNode) => p match {
        case If        (expr,_,_)                        => getAllVal(expr)
        case While     (expr,_,_)                        => getAllVal(expr)
        case Expression(expr,_,_)                        => getAllVal(expr)
        case Switch    (expr,_,_)                        => getAllVal(expr)
        case For       (Some(expr),_,_)                  => getAllVal(expr)
        case Statement (VarDecl(name,typeOf,expr),cr,id) => 
            // for a VarDecl node, we instantiate an artificial assignment because the expr attribute
            // only represents the right part of the assignment included in the declaration
            Set(CFGDecl(p.id,typeOf,name),CFGDef(typeOf,name)) ++ 
            expr.map(e => CFGExpr(BinaryOp(typeOf,SourceCodeNode(DeclRefExpr(typeOf,name,id),cr,id),e,"="))) ++ 
            Set(CFGString(name),CFGString(typeOf))
        case _                                          => Set()
    }
}