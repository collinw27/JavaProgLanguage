package plc.project;

import plc.project.analyzer.AnalyzeException;
import plc.project.analyzer.Analyzer;
import plc.project.analyzer.Type;
import plc.project.evaluator.Environment;
import plc.project.evaluator.EvaluateException;
import plc.project.evaluator.Evaluator;
import plc.project.evaluator.RuntimeValue;
import plc.project.evaluator.Scope;
import plc.project.generator.Generator;
import plc.project.lexer.LexException;
import plc.project.lexer.Lexer;
import plc.project.parser.ParseException;
import plc.project.parser.Parser;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Provides an entry point to a REPL (Read-Eval-Print-Loop) for each part of our
 * project, building up to fully evaluating input programs.
 */
public final class Main {

    private interface Repl {
        void evaluate(String input) throws LexException, ParseException, EvaluateException, AnalyzeException;
    }

    private static final Repl REPL = Main::generator; //edit for manual testing

    public static void main(String[] args) {
        if (true)
        {
            while (true) {
                var input = readInput();
                try {
                    REPL.evaluate(input);
                } catch (LexException | ParseException | EvaluateException | AnalyzeException e) {
                    System.out.println(e.getClass().getSimpleName() + ": " + e.getMessage());
                } catch (RuntimeException e) {
                    e.printStackTrace(System.err);
                }
            }
        }
        else
        {
            try
            {
                String input = new String(Files.readAllBytes(Paths.get("C:\\Users\\colli\\Documents\\School Stuff\\COP 4020\\CLion\\PlcProject (Lexer)\\test.txt")));
                try {
                    REPL.evaluate(input);
                } catch (LexException | ParseException | EvaluateException | AnalyzeException e) {
                    System.out.println(e.getClass().getSimpleName() + ": " + e.getMessage());
                } catch (RuntimeException e) {
                    e.printStackTrace(System.err);
                }
            }
            catch (java.io.IOException e)
            {
                System.out.println("Error reading file: " + e.getMessage());
            }
        }
    }

    private static void lexer(String input) throws LexException {
        var tokens = new Lexer(input).lex();
        System.out.println("List<Token>[size=" + tokens.size() + "]" + (tokens.isEmpty() ? "" : ":"));
        for (var token : tokens) {
            System.out.println(" - " + token);
        }
    }

    private static void parser(String input) throws LexException, ParseException {
        var tokens = new Lexer(input).lex();
        var ast = new Parser(tokens).parse("source"); //edit for manual testing
        System.out.println(prettify(ast.toString()));
    }

    private static final Evaluator EVALUATOR;

    static {
        var scope = plc.project.evaluator.Environment.scope();
        EVALUATOR = new Evaluator(new plc.project.evaluator.Scope(scope));
        scope.define("scope", new RuntimeValue.Function("dynamic_scope", _ -> {
            return new RuntimeValue.Primitive(EVALUATOR.getScope()); //returns *current* scope
        }));
    }

    private static void evaluator(String input) throws LexException, ParseException, EvaluateException {
        var tokens = new Lexer(input).lex();
        var ast = new Parser(tokens).parse("source"); //edit for manual testing
        var value = EVALUATOR.visit(ast);
        System.out.println(prettify(value.toString()));
    }

    private static final Analyzer ANALYZER;

    static {
        var scope = plc.project.analyzer.Environment.scope();
        ANALYZER = new Analyzer(new plc.project.analyzer.Scope(scope));
        scope.define("scope", new Type.Function(List.of(), Type.DYNAMIC));
    }

    private static void analyzer(String input) throws LexException, ParseException, AnalyzeException, EvaluateException {
        var tokens = new Lexer(input).lex();
        var ast = new Parser(tokens).parse("source"); //edit for manual testing
        var ir = ANALYZER.visit(ast);
        System.out.println(prettify(ir.toString()));
        var value = EVALUATOR.visit(ast);
        System.out.println(prettify(value.toString()));
    }

    private static void generator(String input) throws LexException, ParseException, AnalyzeException {
        var tokens = new Lexer(input).lex();
        var ast = new Parser(tokens).parse("source"); //edit for manual testing
        var ir = ANALYZER.visit(ast);
        var code = new Generator().visit(ir);
        System.out.println(code);
    }

    private static final Scanner SCANNER = new Scanner(System.in);

    private static String readInput() {
        var input = SCANNER.nextLine();
        if (!input.isEmpty()) {
            return input;
        }
        System.out.println("Multiline input - enter empty line to submit:");
        var builder = new StringBuilder();
        var next = SCANNER.nextLine();
        while (!next.isEmpty()) {
            builder.append(next).append("\n");
            next = SCANNER.nextLine();
        }
        return builder.toString();
    }

    private static final Pattern RECORD_FORMAT = Pattern.compile(
        "(?<open>)(?<=^|\\[|, |=)[A-Za-z]*\\[(?<inline>(?=[^\\[]*?]))?" +
            "|(?<value>), (?=[A-Za-z]+(\\[|=))" +
            "|(?<close>)](?=,|]|$)"
    );

    private static String prettify(String record) {
        //This is not how you're supposed to use regex, but... it works?
        return RECORD_FORMAT.matcher(record).replaceAll(new Function<>() {
            private String indent = "";
            private boolean inline = false;
            @Override
            public String apply(MatchResult m) {
                if (inline || m.group("inline") != null) {
                    inline = m.group("close") == null;
                    return m.group();
                } else if (m.group("open") != null) {
                    indent = indent + "    ";
                    return m.group() + "\n" + indent;
                } else if (m.group("value") != null) {
                    return ",\n" + indent + m.group().replaceFirst(", ", "");
                } else if (m.group("close") != null) {
                    indent = indent.replaceFirst("    ", "");
                    return "\n" + indent + m.group();
                } else {
                    throw new AssertionError(m.group());
                }
            }
        });
    }

}
