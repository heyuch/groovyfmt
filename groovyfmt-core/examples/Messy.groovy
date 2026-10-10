#!/usr/bin/env groovy
package com.example.ci

import groovy.transform.CompileStatic
import java.time.Instant

/** 构建流水线
 *  故意写得很乱，用来验证 groovyfmt
 */
@CompileStatic
class PipelineBuilder {

    private static final List<String> STAGES=['checkout','build','test']

    String name = "demo-${Instant.now()}"
    def versionPattern = ~/\d+\.\d+\.(\w+)/

    // 主流程
    Map run(Map cfg=[:], Closure body) {
        if(cfg.enabled){
            STAGES.each{ stage ->
                println "stage ${stage} -> ${cfg.url ?: 'n/a'}"
            }
        }
        def parts = cfg.items.collect { it.trim() }.findAll { it }
        def total = parts.size()*2+1
        def label = total > 10 ? 'big' : 'small'
        def joined = parts.join(',')
        def msg = 'a' + \
                  'b'
        body?.call()
        return [ok:true,count:total,label:label,joined:joined,msg:msg]
    }
}

def builder=new PipelineBuilder()
builder.run(enabled:true, items:[' a ','b']) { println 'done' }
