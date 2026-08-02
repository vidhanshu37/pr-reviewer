package com.pr_reviewer.prreviewer.rule;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class JavaAstService {

    public void logFileStructure(String filename, String sourceCode) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(sourceCode);
            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz ->
                    log.info("Parsed class '{}' in {} - methods: {}, fields: {}, annotations: {}",
                            clazz.getNameAsString(),
                            filename,
                            clazz.getMethods().size(),
                            clazz.getFields().size(),
                            clazz.getAnnotations()));
        } catch (Exception e) {
            log.warn("Failed to parse {} as Java source: {}", filename, e.getMessage());
        }
    }
}
