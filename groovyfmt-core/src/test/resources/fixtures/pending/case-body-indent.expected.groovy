class CaseBody {

    def select(int moduleCode) {
        switch (moduleCode) {
            case 1:
                return ['a']
            case 2:
            case 3:
                return ['b']
            default:
                return []
        }
    }

}
