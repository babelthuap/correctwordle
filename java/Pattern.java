import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

class Pattern {
  private static final String FILENAME = "patterns.koko";
  static byte ALL_GREEN = (byte)242;

  private int solutionsSize;
  private byte[] patterns;

  Pattern(List<String> solutionList, List<String> guessList) {
    this.solutionsSize = solutionList.size();
    this.patterns = calculatePatterns(solutionList, guessList);
  }

  byte getPattern(int guessIdx, int solutionIdx) {
    return patterns[guessIdx * solutionsSize + solutionIdx];
  }

  private static byte[] calculatePatterns(List<String> solutionList,
                                          List<String> guessList) {
    List<String> wordList = Lists.concat(solutionList, guessList);
    byte[] patterns = new byte[wordList.size() * solutionList.size()];

    File file = new File(FILENAME);
    try (FileInputStream fis = new FileInputStream(file)) {
      fis.read(patterns);
      return patterns;
    } catch (Exception e) {
      // "patterns" has not been initialized yet
    }

    List<Integer> charSets =
        wordList.stream().map(Pattern::getCharSet).collect(Collectors.toList());
    for (int guessIdx = 0; guessIdx < wordList.size(); guessIdx++) {
      String guess = wordList.get(guessIdx);
      int guessCharSet = charSets.get(guessIdx);
      int offset = guessIdx * solutionList.size();
      for (int solnIdx = 0; solnIdx < solutionList.size(); solnIdx++) {
        int solutionCharSet = charSets.get(solnIdx);
        if ((guessCharSet & solutionCharSet) != 0) {
          String solution = solutionList.get(solnIdx);
          patterns[offset + solnIdx] = calculatePattern(guess, solution);
        }
      }
    }

    try (FileOutputStream fos = new FileOutputStream(FILENAME)) {
      fos.write(patterns);
    } catch (IOException e) {
      System.out.println(e);
    }

    return patterns;
  }

  private static int getCharSet(String w) {
    var bits = 0;
    for (int i = 0; i < w.length(); i++) {
      bits |= 1 << (w.codePointAt(i) - 65);
    }
    return bits;
  }

  private static byte calculatePattern(String guess, String solution) {
    int[] charCounts = new int[26];
    int[] pattern = new int[5]; // init to all grey

    for (int i = 0; i < 5; i++) {
      charCounts[solution.codePointAt(i) - 65]++;
    }

    for (int i = 0; i < 5; i++) {
      int chr = guess.codePointAt(i) - 65;
      if (guess.charAt(i) == solution.charAt(i)) {
        charCounts[chr]--;
        pattern[i] = 2; // green
      } else if (charCounts[chr] > 0) {
        charCounts[chr]--;
        pattern[i] = 1; // yellow
      }
    }

    int base3 = 0;
    int power = 1;
    for (int i = 0; i < 5; i++) {
      base3 += pattern[i] * power;
      power *= 3;
    }
    return (byte)base3;
  }
}
