package cfg


import ast.Expression
import ast.For
import ast.Identifier
import ast.If
import ast.Statement
import ast.While
import ast.model._
import ctl.Bottom
import ctl.Environment
import ctl.Convert
import ctl.MetaVariable
import ctl.Value
import ctl.BindingsEnv
import com.sun.xml.internal.bind.v2.schemagen.episode.Bindings
import ctl.Labelizer
import ast.ProgramNode

/**
 * @author Zohour Abouakil
 * @author Xiaowen Ji
 */


/* 
 * /////////////////////// Generic type definitions : Value and MetaVariable ///////////////////////
 */
case class CfgMetaVar(name: String) extends MetaVariable {
    override def hashCode       = name.hashCode
    
    override def equals(a: Any) = a match {
        case CfgMetaVar(value) => value == name
        case _                 => false 
    }
    
    override def toString       = name
}

sealed abstract class CFGVal extends Value
final case class CFGExpr (expr: Expr)         extends CFGVal
final case class CFGDecl (decl: Decl)         extends CFGVal
final case class CFGBlock(elts: List[CFGVal]) extends CFGVal

/* 
 * /////////////////////// Pattern definitions ///////////////////////
 */
sealed abstract class PatternExpr
case class UndefinedExpr(name: CfgMetaVar) extends PatternExpr
case class DefinedExpr  (expr: Expr  )     extends PatternExpr

trait ExprPattern extends Convert {
    type Env = Environment[CfgMetaVar, CFGVal]
    def matches(expr: Expr): Option[Env]
    
    def matchEnv(pattern: PatternExpr, expr: Expr): Env = pattern match {
            case DefinedExpr  (e   : Expr  )     => if (e matches expr) new BindingsEnv else Bottom
            case UndefinedExpr(name: CfgMetaVar) => new BindingsEnv ++ (name -> expr)
    }
}

// This class works for BinaryOp and CompoundAssignOp
case class BinaryOpPattern (left: PatternExpr, right: PatternExpr, op: String) extends ExprPattern{   

    override def matches(expr: Expr): Option[Env] = {
        expr match {
          case BinaryOp(l,r,operator) => 
              if (operator == op) {
                  val lenv = matchEnv(left ,l)
                  val renv = matchEnv(right,r)
                  (lenv,renv) match {
                      case (BindingsEnv(bind1),BindingsEnv(bind2)) => Some(new BindingsEnv(bind1 ++ bind2))
                      case _                                       => None
                  } 
              }
              else     
                  None
          case _ => None
        }
    }
}

case class UnaryOpPattern (operand: PatternExpr, op: String, kind: OpPosition) extends ExprPattern{

    override def matches(expr: Expr): Option[Env] = {
        expr match {
          case UnaryOp(operand,operator,kind) =>  
              if (operator == op && this.kind == kind) {
                  val env = matchEnv(this.operand, operand)
                  env match {
                      case BindingsEnv(bind) => Some(new BindingsEnv(bind))
                      case _                 => None
                  }
              }
              else 
                  None
          case _ => None
        }
    }
}

///* A revoir */
//case class LiteralPattern(typeName: String, value: String) extends ExprPattern {
//
//    override def matches(expr: Expr): Option[Env] = {
//        expr match {
//          case Literal(tp,vl) =>  
//              if (typeName == tp && value == vl) {
//                  Some(new BindingsEnv)
//              } else {
//                  None
//              }
//          case _ => None
//        }
//    }
//}
//
//case class DeclRefExprPattern(id: String, refType: String) extends ExprPattern {
//
//    override def matches(expr: Expr): Option[Env] = {
//        expr match {
//          case DeclRefExpr(_,_,id,refType) =>  
//              if (this.id == id && this.refType == refType) {
//                Some(new BindingsEnv)
//              } else {
//                  None
//              }
//          case _ => None
//        }
//    }
//}
//
//// patternExprs: (cond,yes,no)
//case class ConditionalOperatorPattern(patternExprs: (PatternExpr,PatternExpr,PatternExpr), rtnType: String) extends ExprPattern {
//    override def matches(expr: Expr): Option[Env] = {
//        expr match {
//          case ConditionalOperator(exprs,rtnType) =>  
//              if (this.rtnType == rtnType) {
//                  val condEnv = matchEnv(patternExprs._1,exprs._1)
//                  val yesEnv  = matchEnv(patternExprs._2,exprs._2)
//                  val noEnv   = matchEnv(patternExprs._3,exprs._3)
//                  (condEnv,yesEnv,noEnv) match {
//                      case (BindingsEnv(cpos),BindingsEnv(ypos),BindingsEnv(npos)) => Some(new BindingsEnv(cpos ++ ypos ++ npos))
//                      case _ => None
//                  }
//              }
//              else 
//                  None
//          case _ => None
//        }
//    }
//}
//
//// patternExprs: (array,index)
//case class ArraySubscriptExprPattern(patternExprs: (PatternExpr,PatternExpr)) extends ExprPattern {
//    override def matches(expr: Expr): Option[Env] = {
//        expr match {
//          case ArraySubscriptExpr(exprs) => 
//              val arrayEnv = matchEnv(patternExprs._1,exprs._1)
//              val indexEnv = matchEnv(patternExprs._2,exprs._2)
//              (arrayEnv,indexEnv) match {
//                  case (BindingsEnv(apos),BindingsEnv(ipos)) => Some(new BindingsEnv(apos ++ ipos))
//                  case _ => None
//              }
//          case _ => None
//        }
//    }
//}

//case class CallExprPattern(params: List[PatternExpr], rtnType: String) extends ExprPattern {
//    override def matches(expr: Expr): Option[Env] = {
//        expr match {
//          case CallExpr(rtnType,params) =>  
//              val binding = new BindingsEnv
//              if (this.rtnType == rtnType) {
//                  for(tup <- this.params.zip(params)) tup match {
//                      case (pe,e) =>
//                          val env = matchEnv(pe,e)
//                          env match {
//                              case BindingsEnv(pos) => binding ++ pos.toSeq
//                              case _ =>
//                          }
//                  }
//                  Some(binding)
//              }
//              else 
//                  None
//          case _ => None
//        }
//    }
//}
//
//case class InitListExprPattern(exprs: List[PatternExpr]) extends ExprPattern {
//    override def matches(expr: Expr): Option[Env] = {
//        expr match {
//          case InitListExpr(exprs) =>  
//                val binding = new BindingsEnv()
//                for(tup <- this.exprs.zip(exprs)) tup match {
//                    case (pe,e) =>
//                        val env = matchEnv(pe,e)
//                        env match {
//                            case BindingsEnv(pos) => binding ++ pos 
//                            case _ =>
//                        }
//                }
//                Some(binding)
//          case _ => None
//        }
//    }
//}

/* 
 * /////////////////////// Labilizer definition ///////////////////////
 */


class IfLabelizer(val pattern: ExprPattern) extends Labelizer[CfgMetaVar, ProgramNode, CFGVal] {
    override def test(t: ProgramNode) = t match {
        case If(expr,_,_) => pattern.matches(expr) 
        case _            => None 
    }
}

class ForLabelizer(val pattern: ExprPattern) extends Labelizer[CfgMetaVar, ProgramNode, CFGVal]  {
    override def test(t: ProgramNode) = t match {
        case For(Some(expr),_,_) => pattern.matches(expr) 
        case _                   => None 
    }
}

class WhileLabelizer(val pattern: ExprPattern) extends Labelizer[CfgMetaVar, ProgramNode, CFGVal]  {
    override def test(t: ProgramNode) = t match {
        case While(expr,_,_) => pattern.matches(expr)
        case _               => None 
    }
}

//class StatementLabelizer(val pattern: ExprPattern) extends ProgramNodeLabelizer {
//    override def visitExpression(expr: Expression) = pattern.matches(expr.e)
//}
//
//class IdentifierLabelizer(val pattern: ExprPattern) extends Labelizer[CfgMetaVar, ProgramNode, CFGVal] {
//    override def visitIdentifier(id: Identifier) = id match { case Identifier(s,_,_)  => None }
//}
//
class ExpressionLabelizer(val pattern: ExprPattern) extends Labelizer[CfgMetaVar, ProgramNode, CFGVal] {
    override def test(t: ProgramNode) = t match {
        case Expression(e,_,_) => pattern.matches(e)
        case _                 => None 
    }
}