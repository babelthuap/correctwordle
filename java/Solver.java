import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class Solver {
  public static void main(String[] args) {
    var start = System.currentTimeMillis();

    var solutionList = Lists.readList("solution_list.txt");
    var guessList = Lists.readList("guess_list.txt");
    var solver = new Solver(solutionList, guessList);
    solver.getOptimal();

    System.out.println((System.currentTimeMillis() - start) + " ms");
  }

  private List<String> SOLUTION_LIST;
  private List<String> GUESS_LIST;
  private List<String> WORD_LIST;
  private Map<List<Integer>, Optimal> MEMO;
  private Pattern PATTERN;

  Solver(List<String> solutionList, List<String> guessList) {
    this.SOLUTION_LIST = solutionList;
    this.GUESS_LIST = guessList;
    this.WORD_LIST = Lists.concat(SOLUTION_LIST, GUESS_LIST);
    this.PATTERN = new Pattern(SOLUTION_LIST, GUESS_LIST);
    this.MEMO = new HashMap<>();
  }

  class Optimal implements Comparable<Optimal> {
    public List<Integer> guesses;
    public float value;

    public Optimal(List<Integer> guesses, float value) {
      this.guesses = guesses;
      this.value = value;
    }

    @Override
    public int compareTo(Optimal other) {
      return this.value != other.value
          ? Float.compare(this.value, other.value)
          : this.guesses.get(0).compareTo(other.guesses.get(0));
    }

    @Override
    public String toString() {
      return value + ": " + printList(guesses);
    }
  }

  private String printList(List<Integer> list) {
    String str = "[";
    for (int i = 0; i < list.size(); i++) {
      if (i > 0) {
        str += ",";
      }
      str += WORD_LIST.get(list.get(i));
    }
    return str + "]";
  }

  void getOptimal() {
    var solnSet = new ArrayList<Integer>();
    for (int i = 0; i < SOLUTION_LIST.size(); i++) {
      solnSet.add(i);
    }
    System.out.println("solnSet: " + printList(solnSet));

    System.out.println("availableProcessors: " +
                       Runtime.getRuntime().availableProcessors());
    ExecutorService executor = Executors.newWorkStealingPool(
        Math.max(1, Runtime.getRuntime().availableProcessors()));

    ConcurrentSkipListSet<Optimal> results = new ConcurrentSkipListSet<>();
    try {
      executor
          .submit(() -> {
            IntStream.range(0, WORD_LIST.size())
                .parallel()
                .mapToObj(i -> {
                  var opt = getOptimal(solnSet, i);
                  System.out.println(opt);
                  return opt;
                })
                .forEach(results::add);
          })
          .get();
    } catch (Exception e) {
      e.printStackTrace();
    }

    System.out.println();
    System.out.println("top results:");
    results.stream().sorted().limit(10).forEach(System.out::println);
    System.out.println();

    try (FileWriter writer = new FileWriter("results.txt")) {
      for (var result :
           results.stream().sorted().collect(Collectors.toList())) {
        writer.write(result.toString());
        writer.write(System.getProperty("line.separator"));
      }
      writer.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  Optimal getOptimal(List<Integer> solnSet) { return getOptimal(solnSet, -1); }

  Optimal getOptimal(List<Integer> solnSet, int specifiedGuess) {
    switch (solnSet.size()) {
    case 0:
      throw new RuntimeException("Called getOptimal with no input");
    case 1:
      return new Optimal(solnSet, 1.0f);
    case 2:
      return new Optimal(solnSet, 1.5f);
    default:
      // Calculate recursively...
    }

    Optimal cached = MEMO.get(solnSet);
    if (cached != null) {
      return cached;
    }

    var optimal = new Optimal(new ArrayList<>(), Float.MAX_VALUE);

    if (specifiedGuess != -1) {
      processValueForGuess(specifiedGuess, solnSet, optimal);
    } else {
      // Try guesses in the solution set first (for suitably small solution
      // sets)
      if (solnSet.size() < 12) {
        for (var guess : solnSet) {
          processValueForGuess(guess, solnSet, optimal);
        }
      }
      // The best we can do for non-solutions is 2. If we haven't already
      // reached that, then try all possible guesses.
      if (optimal.value > 2 * solnSet.size()) {
        for (int guess = 0; guess < WORD_LIST.size(); guess++) {
          processValueForGuess(guess, solnSet, optimal);
        }
      }
    }

    optimal.value /= solnSet.size();
    if (specifiedGuess == -1) {
      MEMO.put(solnSet, optimal);
    }
    return optimal;
  }

  private void processValueForGuess(int guess, List<Integer> solnSet,
                                    Optimal optimal) {
    Map<Byte, List<Integer>> groups = splitIntoGroups(guess, solnSet);
    if (groups.size() == 1) {
      // This guess does not help at all!
      return;
    }
    float valueForThisGuess = 0f;
    for (var entry : groups.entrySet()) {
      byte pattern = entry.getKey();
      List<Integer> group = entry.getValue();
      if (pattern == Pattern.ALL_GREEN) {
        // We solve it immediately (i.e., in 1 step)
        valueForThisGuess += 1.0f;
      } else {
        switch (group.size()) {
        case 1:
          valueForThisGuess += 2.0f; // 1 * (1.0 + 1.0)
          break;
        case 2:
          valueForThisGuess += 5.0f; // 2 * (1.0 + 1.5)
          break;
        default:
          valueForThisGuess += group.size() * (1.0 + getOptimal(group).value);
        }
      }
      if (valueForThisGuess > optimal.value) {
        // We already know it's not going to be better
        return;
      }
    }
    if (valueForThisGuess < optimal.value) {
      optimal.guesses.clear();
      optimal.guesses.add(guess);
      optimal.value = valueForThisGuess;
    } else if (valueForThisGuess == optimal.value) {
      optimal.guesses.add(guess);
    }
  }

  private Map<Byte, List<Integer>> splitIntoGroups(int guess,
                                                   List<Integer> solnSet) {
    Map<Byte, List<Integer>> groups = new HashMap<>();
    for (int soln : solnSet) {
      byte pattern = PATTERN.getPattern(guess, soln);
      if (groups.containsKey(pattern)) {
        groups.get(pattern).add(soln);
      } else {
        var group = new ArrayList<Integer>();
        group.add(soln);
        groups.put(pattern, group);
      }
    }
    return groups;
  }
}
