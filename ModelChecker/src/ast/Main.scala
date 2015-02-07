package ast

import ast.model.CompoundStmt
import scala.sys.process._
import ast.model.NullStmt
import java.io.PrintWriter
import java.io.File

object Main extends App {
    val folder = "while"
    new File("unitary_tests/%s/".format(folder)).listFiles.filter(_.getName.endsWith("cpp")).foreach { file => 
        val name = file.getName
        val s    = name.substring(0,name.lastIndexOf('.'))
        val path = file.getPath.substring(0,file.getPath.indexOf(name))
        
        // generate the Clang AST and print it in a file
        val clangPath = path + s + ".txt"
        println(clangPath)
        val cmd    = "clang -Xclang -ast-dump -fsyntax-only " + file.getPath
        var writer = new PrintWriter(clangPath)
        writer.write(cmd.!!)
        writer.close
        
        // parse the AST
    	val parseResult = (new ASTParser).parseFile(clangPath)
    	val astRes      = new SourceCodeNodeFactory(parseResult.root).result

    	// generate the CFG and write it in a file
    	val cfg = new ProgramNodeFactory(astRes.rootNodes(0), astRes.labelNodes).result
    	writer  = new PrintWriter(path + "test.dot")
    	writer.write("digraph {\n%s}".format(cfg.mkString))
    	writer.close
    	
    	// generate the png image
    	Seq("dot","-Tpng",path + "test.dot","-o",path + s + ".png") !
    }
}