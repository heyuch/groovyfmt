package io.github.heyuch.groovyfmt.cst

import groovy.transform.CompileStatic

/** 词法 / 语法错误。抛出即代表"这个文件我们不碰"（fail-closed）。 */
@CompileStatic
class GroovySyntaxException extends Exception {

    final List<String> errors

    GroovySyntaxException(List<String> errors) {
        super(errors.join('; '))
        this.errors = List.copyOf(errors)
    }

}
