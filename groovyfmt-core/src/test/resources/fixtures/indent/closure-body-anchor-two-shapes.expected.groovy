class ClosureBodyAnchor {

    void trailing(resultDTO, outputFileDO) {
        boolean isLocked = MutexLock.lockAndRun("outputFile-${outputFileDO.outputFileId}".toString(),
                MySchedule.TU_2_MINUTES.intValue(), MySchedule.TU_10_SECONDS.intValue(), {
            resultDTO = processOutputFileInternal(outputFileDO.outputFileId)
        }, this)
    }

    void solo(contentUtils, content, version) {
        contentUtils.when({ -> ContentUtils.executeAndGetContentDataset(eq(content), eq(version)) })
                .thenAnswer { invocation ->
                    return invocation.getArgument(2)
                }
    }

}
