package io.github.heyuch.groovyfmt.pipeline

import groovy.transform.CompileStatic

/** 保证文件以换行结尾（POSIX 约定，也让 git diff 更干净）。 */
@CompileStatic
class FinalNewlineStep implements FormatterStep {

    @Override
    String getName() {
        'ensure-final-newline'
    }

    @Override
    String apply(String source) {
        if (source.empty || source.endsWith('\n')) {
            return source
        }
        source + '\n'
    }

}
