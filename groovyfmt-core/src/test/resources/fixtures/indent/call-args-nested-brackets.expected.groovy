class CallArgsNestedBrackets {

    def save(cell, valueDsl) {
        dslClient.batchSave([new DslSaveCmd(
                tenantCode:cell.tenantCode,
                refType:RefTypeEnum.OUTPUT_HF_PATTERN,
                dslCode:OutputHfPatternDslCodeUtils.cellDslCode(cell.cellId),
                dslContent:valueDsl
        )])
    }

}
