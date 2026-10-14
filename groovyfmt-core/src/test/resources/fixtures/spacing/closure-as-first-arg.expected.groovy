class ClosureAsFirstArg {

    void run(fid) {
        TransactionUtils.withoutTransAfterCurrentTransCommitted({
            blobService.deleteFile(fid)
        })
        eachItem({ it -> println it })
    }

}
