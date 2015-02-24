package ctl

import scala.reflect.runtime.universe._

/**
 * @author Zohour Abouakil
 * @author David Courtinot
 */
class ModelChecker[M <: MetaVariable: TypeTag, N, V <: Value : TypeTag](private val root: GraphNode[N], convert: N => Set[V]) {
    type StateEnv      = (GNode, Environment[M, V])
    type GNode         = GraphNode[N]
    type CheckerResult = Set[StateEnv]
    
    private lazy val Val: Set[V] = root.states.flatMap(g => convert(g.value)).toSet
    
    def evalExpr(expr : CtlExpr[M,N,V]): CheckerResult = expr match {
            case And      (x, y) => conj  (evalExpr(x),evalExpr(y))
            case Or       (x, y) => disj  (evalExpr(x),evalExpr(y))
            case AU       (x, y) => SAT_AU(evalExpr(x),evalExpr(y))
            case EU       (x, y) => SAT_EU(evalExpr(x),evalExpr(y))
            case AX       (x   ) => preA  (evalExpr(x))
            case EX       (x   ) => preE  (evalExpr(x))
            case Not      (x   ) => neg   (evalExpr(x))
            case Exists   (x, y) => exists(x,evalExpr(y))
            case Predicate(x   ) => for (n <- root.states ; env = x.test(n.value) ; if(env.isDefined)) yield (n,env.get)         
    }
    
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
    def inj(s: GNode, env: Environment[M,V])        = (s,env)
    def same(t1: CheckerResult , t2: CheckerResult) = t1 == t2
    
    def negone(se: StateEnv): Set[StateEnv] = se match { 
        case (s, env) => 
            (!env).map { case value => (s, value) } ++ (root.states - s).map(inj(_, new BindingsEnv[M,V]))    
    }

    protected def ex_binding(varName : M, typeOf: TypeOf[V], se: StateEnv) = se._2 match { 
        case BindingsEnv(bind) => bind.get(varName) match {
            case Some(NegBinding(neg)) => typeOf.filter(Val).size > neg.size
            case _                     => true
        }        
        case _ => false
    }
    
    def conj(T1: CheckerResult , T2: CheckerResult) = {
        for (t1 <- T1 ; t2 <- T2 ; inter = interStateEnv(t1,t2) ; if (inter.isDefined)) yield inter.get
    }
        
    def disj    (t1: CheckerResult , t2: CheckerResult)   = t1 ++ t2
    def shift   (s1: GNode , T: CheckerResult, s2: GNode) = T.filter { case(a,b) => a == s1 }.map{ case(a,b) => (s2,b) }
    def Disj    (x: Set[CheckerResult])                   = x.foldLeft(Set[StateEnv]())(disj)
    def conjFold(x: Set[CheckerResult]): CheckerResult    = 
        if (x.isEmpty) Set() else x.foldLeft(root.states.map(node => inj(node, new BindingsEnv)))(conj)
    
    def neg     (T: CheckerResult)                        = conjFold(T.map(negone))
    def exists(varType: (M,TypeOf[V]), T: CheckerResult)     = for (t <- T ; if (ex_binding(varType._1, varType._2,t))) yield existsone(varType._1,t)
    
    def preA(T: CheckerResult) = root.states.flatMap(s => conjFold(s.next.toSet.map((sNext: GNode) => shift(sNext,T,s))))   
    def preE(T: CheckerResult) = root.states.flatMap(s => Disj(s.next.toSet.map((sNext: GNode) => shift(sNext,T,s))))   

    def SAT_AU                 = SAT_UU(preA)(_,_)
    def SAT_EU                 = SAT_UU(preE)(_,_)
    
    private def SAT_UU(f: CheckerResult => CheckerResult)(T1: CheckerResult , T2: CheckerResult) = {
        var (w,y,x) = (T1,T2,T2)
        do {
//            println("w :: " + w + "\n , x :: " + x + "\n , y :: " + y)
            
            x = y 
            y = disj(y, conj(w, f(y)))
            
//            println("Le pre " + f(y) + " Le conj " + conj(w, f(y)))
            
//            println("\nx :: " + x + "\n , y :: " + y)
            
        } while(!same(x,y))           
            println("\nJe sors => x :: " + x + " , y :: " + y)
        y
    }
}