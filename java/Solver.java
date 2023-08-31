class Solver {
  public static void main(String[] args) {
    var start = System.currentTimeMillis();

    var solutionList = Lists.readList("solution_list.txt");
    var guessList = Lists.readList("guess_list.txt");

    var pattern = new Pattern(solutionList, guessList);
    System.out.println("0, 0: " + pattern.getPattern(0, 0));
    System.out.println("ALL_GREEN: " + Pattern.ALL_GREEN);

    System.out.println((System.currentTimeMillis() - start) + " ms");
  }
}
