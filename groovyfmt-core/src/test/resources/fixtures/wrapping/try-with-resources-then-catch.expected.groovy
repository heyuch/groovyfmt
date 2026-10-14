class TryWithResourcesThenCatch {

    private static void m(String license) {
        try (ByteArrayInputStream licenseStream = new ByteArrayInputStream(license.getBytes(StandardCharsets.UTF_8))) {
            new License().setLicense(licenseStream)
        } catch (Exception exception) {
            Panic.notExpectedResult("Output Header/Footer cannot load Aspose license", exception)
        }
    }

}
