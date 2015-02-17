package ctl.experiment.generics.simple.gen

import ast.ProgramNodeLabelizer
import ast.ProgramNode

import scala.reflect.runtime.universe._


/**
 * @author Zohour Abouakil
 */
class ModelChecker[M <: MetaVariable: TypeTag, N, V <: Value : TypeTag](val root: GraphNode[N], convert: GraphNode[N] => Set[V]) {
    type StateEnv      = (GNode, Environment[M, V])
    type GNode         = GraphNode[N]
    type CheckerResult = Set[StateEnv]
    
    lazy val Val: Set[V] = root.states.flatMap(convert).toSet
    
    def evalExpr(expr : CtlExpr[M,N,V]): CheckerResult = expr match {
            case And      (x, y)  => conj  (evalExpr(x),evalExpr(y))
            case Or       (x, y)  => disj  (evalExpr(x),evalExpr(y))
            case _AU      (x, y)  => SAT_AU(evalExpr(x),evalExpr(y))
            case _EU      (x, y)  => SAT_EU(evalExpr(x),evalExpr(y))
            case AX       (x   )  => preA  (evalExpr(x))
            case EX       (x   )  => preE  (evalExpr(x))
            case Not      (x   )  => neg   (evalExpr(x))
            case Exists   (x, y) => exists(x,evalExpr(y))
            case Predicate(x)  => for (n <- root.states ; env = x.test(n.value) ; if(env.isDefined)) yield (n,env.get)         
    }
    
    //////////////////////////---------------------------------------------//////////////////////////
    ///////////////////////////// Function implementation only for CTL-V //////////////////////////// 
    //////////////////////////---------------------------------------------//////////////////////////
    def shift(s1: GNode , T: CheckerResult, s2: GNode) = T.filter { case(a,b) => a == s1 }.map{ case(a,b) => (s2,b) }
    
    def interStateEnv(se1: StateEnv, se2: StateEnv): Option[StateEnv] = {
        if (se1._1 != se2._1) None
        else se1._2 & se2._2 match {
            case BindingsEnv(b) => Some((se1._1,BindingsEnv(b)))
            case _ => None
        }              
    }
    
    /**
     * The existsone method discards the binding of a quantified variable x from the Environment of a state/Environment pair
     */
    def existsone(metaData: M, ev: StateEnv): StateEnv = (ev._1,ev._2 - metaData)
    
    /**
     * The function inj, used to inject the result of matching a predicate into the codomain of SAT
     */
    protected def inj(s: GNode, env: Environment[M,V])        = (s,env)
    protected def same(t1: CheckerResult , t2: CheckerResult) = t1 == t2
    
    def negone(se: StateEnv): Set[StateEnv] = se match { 
        case (s, env) => 
            (!env).map { case value => (s, value) } ++ (root.states - s).map(inj(_, new BindingsEnv[M,V]))    
    }

    protected def ex_binding(typeOf: TypeOf[M,V], se: StateEnv) = se._2 match { 
        case BindingsEnv(bind) => bind.get(typeOf.varName) match {
            case Some(NegBinding(neg)) => typeOf.filter(Val).size > neg.size
            case _                     => true
        }        
        case _ => false
    }
    
    //////////////////////////---------------------------------------------///////////////////////////
    //////////////////////////////////////// common functions //////////////////////////////////////// 
    //////////////////////////---------------------------------------------///////////////////////////
    def disj(t1: CheckerResult , t2: CheckerResult) = t1 ++ t2
    
    def Disj(x: Set[CheckerResult]) = x.foldRight(Set[StateEnv]())(disj)
    
    def conj(T1: CheckerResult , T2: CheckerResult) = 
        for (t1 <- T1 ; t2 <- T2 ; inter = interStateEnv(t1,t2) ; if (inter.isDefined)) yield inter.get
        
    def conjFold(x: Set[CheckerResult]) = x.foldRight(root.states.map(node => inj(node, new BindingsEnv)))(conj)
        
    def neg(T: CheckerResult) = conjFold(T.map(negone))
    
    // s∈ States (Conj {shift(s 0 , T, s) | s 0 ∈ next(s)})
    def preA(T: CheckerResult) = root.states.flatMap(s => conjFold(s.next.toSet.map((sNext: GNode) => shift(sNext,T,s))))
    
    def preE(T: CheckerResult) = root.states.flatMap(s => Disj(s.next.toSet.map((sNext: GNode) => shift(sNext,T,s))))
    
    private def SAT_UU(f: CheckerResult => CheckerResult)(T1: CheckerResult , T2: CheckerResult) = {
            var (w,x,y) = (T1,T2,T2)
            do {
                x = y 
                y = disj(y, conj(w, f(y)))
            } while(!same(x,y))           
            y
        }
    
    def SAT_AU = SAT_UU(preA)(_,_)
    
    def SAT_EU = SAT_UU(preE)(_,_)
    
    def exists(typeOf: TypeOf[M,V], T: CheckerResult) = for (t <- T ; if (ex_binding(typeOf,t))) yield existsone(typeOf.varName,t)
}