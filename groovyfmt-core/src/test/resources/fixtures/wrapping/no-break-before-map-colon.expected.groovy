class NoBreakBeforeMapColon {

    void m(fileName, fileData) {
        return new MyFile(fileName:"Insurer hierarchy initial data upload template (${AppContext.instance.tenantCode} - ${System.currentTimeMillis()})",
                mimeType:GlobalConst.MIME_TYPE_EXCEL_2007,
                fdata:fileData)
    }

}
