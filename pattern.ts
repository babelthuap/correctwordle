import {readFileSync, writeFileSync} from 'fs';

let PATTERNS: Uint8Array|undefined;
let SOLUTIONS_LENGTH: number|undefined;

export function getPattern(guessIdx: number, solutionIdx: number) {
  return PATTERNS![SOLUTIONS_LENGTH! * guessIdx + solutionIdx];
}

export function init(solutionList: string[], guessList: string[]) {
  console.time('pattern init');
  SOLUTIONS_LENGTH = solutionList.length;
  try {
    PATTERNS = new Uint8Array(readFileSync('patterns'));
    if (PATTERNS.length !==
        (solutionList.length + guessList.length) * solutionList.length) {
      throw 'wrong length';
    }
    console.timeEnd('pattern init');
  } catch (e) {
    const wordList = [...solutionList, ...guessList];
    const charSets = Uint32Array.from(wordList, getCharSet);
    PATTERNS = new Uint8Array(wordList.length * SOLUTIONS_LENGTH);
    for (let guessIdx = 0; guessIdx < wordList.length; guessIdx++) {
      const guess = wordList[guessIdx];
      const guessCharSet = charSets[guessIdx];
      const offset = guessIdx * SOLUTIONS_LENGTH;
      for (let solnIdx = 0; solnIdx < SOLUTIONS_LENGTH; solnIdx++) {
        const solutionCharSet = charSets[solnIdx];
        if ((guessCharSet & solutionCharSet) !== 0) {
          const solution = solutionList[solnIdx];
          PATTERNS[offset + solnIdx] = calculatePattern(guess, solution);
        }
      }
    }
    writeFileSync('patterns', PATTERNS);
    console.timeEnd('pattern init');
  }
}

export function getCharSet(w: string) {
  let int = 0;
  for (let i = 0; i < w.length; i++) {
    int |= 1 << (w.charCodeAt(i) - 65);
  }
  return int;
}

const charCounts_ = new Uint8Array(26);
const pattern_ = new Uint8Array(5);
function calculatePattern(guess: string, solution: string) {
  charCounts_.fill(0);
  pattern_.fill(0);  // init to all grey
  for (let i = 0; i < 5; i++) {
    charCounts_[solution.charCodeAt(i) - 65]++;
  }
  for (let i = 0; i < 5; i++) {
    const char = guess.charCodeAt(i) - 65;
    if (guess[i] === solution[i]) {
      charCounts_[char]--;
      pattern_[i] = 2;  // green
    } else if (charCounts_[char] > 0) {
      charCounts_[char]--;
      pattern_[i] = 1;  // yellow
    }
  }
  return pattern_.reduce((base3, d, i) => base3 + d * (3 ** i), 0);
}
