module demo::lang::Pico::Plugin

import Prelude;
import util::IDE;
import util::ValueUI; 

import vis::Figure;
import vis::Render;

import demo::lang::Pico::Abstract;
import demo::lang::Pico::Syntax;
import demo::lang::Pico::Typecheck;
import demo::lang::Pico::Eval;
import demo::lang::Pico::Compile;
import demo::lang::Pico::ControlFlow;
import demo::lang::Pico::Uninit;
import demo::lang::Pico::Visualize;

// /*1*/ define the language name and extension

private str Pico_NAME = "Pico";
private str Pico_EXT = "pico";

// /*2*/ Define the connection with the Pico parser
Tree parsePico(str x, loc l) {
    return parse(#Program, x, l);
}

// /*3*/ Define connection with the Pico checkers
// (includes type checking and uninitialized variables check)

public Tree checkPicoProgram(Tree x) {
	p = implode(#PROGRAM, x);
	env = checkProgram(p);
	errors = { error(v, l) | <loc l, PicoId v> <- env.errors };
	if(!isEmpty(errors))
		return x[@messages = errors];
    ids = uninitProgram(p);
	warnings = { warning("Variable <v> maybe uninitialized", l) | <loc l, PicoId v, STATEMENT _> <- ids };
	return x[@messages = warnings];
}

// /*4*/ Define the connection with the Pico evaluator

public void evalPicoProgram(Tree x, loc _) {
	m = implode(#PROGRAM, x); 
	text(evalProgram(m));
}

// /*5*/ Define connection with the Pico compiler

public void compilePicoProgram(Tree x, loc _){
    p = implode(#PROGRAM, x);
    asm = compileProgram(p);
	text(asm);
}

// /*6*/ Define connection with CFG visualization

public void visualizePicoProgram(Tree x, loc _) {
	m = implode(#PROGRAM, x); 
	CFG = cflowProgram(m);
	render(visCFG(CFG.graph));
}
	
// /*7*/ Define all contributions to the Pico IDE

public set[Contribution] Pico_CONTRIBS = {
	popup(
		menu("Pico",[
		    action("Evaluate Pico program", evalPicoProgram),
    		action("Compile Pico to ASM", compilePicoProgram),
    		action("Show Control flow graph", visualizePicoProgram)
	    ])
  	)
};

// /*8*/ Register the Pico tools

public void registerPico() {
  registerLanguage(Pico_NAME, Pico_EXT, parsePico);
  registerAnnotator(Pico_NAME, checkPicoProgram);
  registerContributions(Pico_NAME, Pico_CONTRIBS);
}


