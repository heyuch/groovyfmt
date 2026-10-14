class OnlyInClosureBreakPoints {

    void m() {
        runSomethingVeryLongIndeed { element -> element.getVeryLongPropertyNameThatGoesOnAndOnAndOnAndOn }
    }

}
