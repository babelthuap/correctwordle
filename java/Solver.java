import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

class Solver {
  public static void main(String[] args) {
    var solutionList = Lists.readList("solution_list.txt");
    var guessList = Lists.readList("guess_list.txt");
    var solver = new Solver(solutionList, guessList);
    solver.getOptimal(args.length > 0 ? Integer.parseInt(args[0])
                                      : solutionList.size());
  }

  private static final String MEMO_FILENAME = "memo.koko";
  private static final String RESULTS_FILENAME = "results.txt";

  private List<String> SOLUTION_LIST;
  private List<String> GUESS_LIST;
  private List<String> WORD_LIST;
  private ConcurrentHashMap<List<Integer>, Float> MEMO;
  private Pattern PATTERN;

  @SuppressWarnings("unchecked")
  Solver(List<String> solutionList, List<String> guessList) {
    this.SOLUTION_LIST = solutionList;
    this.GUESS_LIST = guessList;
    this.WORD_LIST = Lists.concat(SOLUTION_LIST, GUESS_LIST);
    this.PATTERN = new Pattern(SOLUTION_LIST, GUESS_LIST);

    try (FileInputStream fis = new FileInputStream(MEMO_FILENAME);
         ObjectInputStream ois = new ObjectInputStream(fis)) {
      this.MEMO = (ConcurrentHashMap<List<Integer>, Float>)ois.readObject();
    } catch (Exception e) {
      this.MEMO = new ConcurrentHashMap<>();
    }
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

  void getOptimal(int solnSetSize) {
    var start = System.currentTimeMillis();

    var solnSet = new ArrayList<Integer>();
    for (int i = 0; i < solnSetSize; i++) {
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
            final AtomicInteger n = new AtomicInteger(0);
            IntStream.range(0, WORD_LIST.size())
                .parallel()
                .mapToObj(i -> {
                  System.out.printf("guess %s / %s => %s\n",
                                    n.incrementAndGet(), WORD_LIST.size(),
                                    WORD_LIST.get(i));
                  Float optimalValue = getOptimal(solnSet, i);
                  System.out.println(WORD_LIST.get(i) + ": " + optimalValue);
                  var list = new ArrayList<Integer>();
                  list.add(i);
                  return new Optimal(list, optimalValue);
                })
                .forEach(results::add);
          })
          .get();
    } catch (Exception e) {
      e.printStackTrace();
    }

    List<Optimal> groupResults = new ArrayList<>();
    float value = 0;
    for (var result : results) {
      if (result.value == value) {
        groupResults.get(groupResults.size() - 1)
            .guesses.addAll(result.guesses);
      } else {
        value = result.value;
        groupResults.add(new Optimal(result.guesses, result.value));
      }
    }

    System.out.println();
    System.out.println("top results:");
    groupResults.subList(0, 3).forEach(System.out::println);
    System.out.println();

    System.out.println("getOptimal(" + solnSetSize + ") => " +
                       (System.currentTimeMillis() - start) + " ms");

    // Save results to file
    try (FileWriter writer = new FileWriter(RESULTS_FILENAME)) {
      for (var result : groupResults) {
        writer.write(result.toString());
        writer.write(System.getProperty("line.separator"));
      }
      writer.close();
    } catch (IOException e) {
      e.printStackTrace();
    }

    // Save MEMO to file
    try (FileOutputStream fos = new FileOutputStream(MEMO_FILENAME);
         ObjectOutputStream oos = new ObjectOutputStream(fos)) {
      oos.writeObject(MEMO);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  Float getOptimal(List<Integer> solnSet) { return getOptimal(solnSet, -1); }

  Float getOptimal(List<Integer> solnSet, int specifiedGuess) {
    switch (solnSet.size()) {
    case 0:
      throw new RuntimeException("Called getOptimal with no input");
    case 1:
      return 1.0f;
    case 2:
      return 1.5f;
    default:
      // Calculate recursively...
    }

    Float cached = MEMO.get(solnSet);
    if (cached != null) {
      return cached;
    }

    float optimal = Float.MAX_VALUE;

    if (specifiedGuess != -1) {
      optimal = processValueForGuess(specifiedGuess, solnSet, optimal);
    } else {
      // Try guesses in the solution set first (for suitably small solution
      // sets)
      if (solnSet.size() < 12) {
        for (var guess : solnSet) {
          optimal = processValueForGuess(guess, solnSet, optimal);
        }
      }
      // The best we can do for non-solutions is 2. If we haven't already
      // reached that, then try all possible guesses.
      if (optimal > 2 * solnSet.size()) {
        for (int guess = 0; guess < WORD_LIST.size(); guess++) {
          optimal = processValueForGuess(guess, solnSet, optimal);
        }
      }
    }

    optimal /= solnSet.size();
    if (specifiedGuess == -1) {
      MEMO.putIfAbsent(solnSet, optimal);
    }
    return optimal;
  }

  private float processValueForGuess(int guess, List<Integer> solnSet,
                                     float optimal) {
    Map<Byte, List<Integer>> groups = splitIntoGroups(guess, solnSet);
    if (groups.size() == 1) {
      // This guess does not help at all!
      return optimal;
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
          valueForThisGuess += group.size() * (1.0 + getOptimal(group));
        }
      }
      if (valueForThisGuess > optimal) {
        // We already know it's not going to be better
        return optimal;
      }
    }
    return valueForThisGuess < optimal ? valueForThisGuess : optimal;
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
