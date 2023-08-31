import guessList from './guess_list.json';
import {getPattern, init} from './pattern';
import solutionList from './solution_list.json';

const wordList = [...solutionList, ...guessList];

function main() {
  init(solutionList, guessList);

  console.time('main');

  // const guess = 'DOTER';
  // console.log('guess:', guess);
  // const groups = splitIntoGroups(
  //     wordList.indexOf(guess), new Array(10).fill(0).map((_, i) => i));
  // console.log([...groups.entries()].map(([k, v]) => {
  //   return `${k.toString(3).padEnd(5, '0').split('').reverse().join('')} =>
  //   ${
  //       v.map(i => wordList[i])}`;
  // }));

  // const rand = (n: number) => Math.floor(Math.random() * n);
  // const solnSet = new Array(20).fill(0).map((_, i) => i + 1000);
  const solnSet =
      shuffle(new Array(solutionList.length).fill(0).map((_, i) => i))
          .slice(0, 30)
          .sort((a, b) => a - b);
  console.log('solution set:', solnSet.map(i => wordList[i]));
  const optimal = getOptimal(solnSet);
  console.log('optimal guesses:', optimal.guesses.map(i => wordList[i]));
  console.log('expected value:', optimal.value);

  console.timeEnd('main');
}

function rand(n: number) {
  return Math.floor((n + 1) * Math.random());
}

function shuffle<T>(arr: T[]): T[] {
  for (let i = arr.length - 1; i > 0; i--) {
    let j = rand(i);
    let temp = arr[j];
    arr[j] = arr[i];
    arr[i] = temp;
  }
  return arr;
}

const MEMO: Map<number, Optimal> = new Map();
function getOptimal(solnSet: number[]): Optimal {
  switch (solnSet.length) {
    case 0:
      throw new Error('Called getOptimal with no input');
    case 1:
      return {guesses: solnSet.slice(0), value: 1};
    case 2:
      return {guesses: solnSet.slice(0), value: 1.5};
    default:
      // Calculate recursively...
  }

  const hash = cyrb53(solnSet);
  const cached = MEMO.get(hash);
  if (cached !== undefined) {
    return cached;
  }

  const optimal: Optimal = {guesses: [], value: Infinity};

  // Try guesses in the solution set first
  for (const guess of solnSet) {
    const groups = splitIntoGroups(guess, solnSet);
    let valueForThisGuess = 0;
    for (const [pattern, group] of groups) {
      if (pattern === 242) {
        // 242 == all green, meaning we solve it immediately
        valueForThisGuess += 1;
      } else {
        valueForThisGuess += group.length * (1 + getOptimal(group).value);
      }
    }
    valueForThisGuess /= solnSet.length;
    if (valueForThisGuess < optimal.value) {
      optimal.guesses.length = 0;
      optimal.guesses.push(guess);
      optimal.value = valueForThisGuess;
    } else if (valueForThisGuess === optimal.value) {
      optimal.guesses.push(guess);
    }
  }

  // The best we can do for non-solutions is 2. If we haven't already reached
  // that, then try all possible guesses.
  if (optimal.value > 2) {
    for (let guess = 0; guess < wordList.length; guess++) {
      const groups = splitIntoGroups(guess, solnSet);
      if (groups.size === 1) {
        // This guess does not help at all!
        continue;
      }
      let valueForThisGuess = 0;
      for (const [pattern, group] of groups) {
        if (pattern === 242) {
          // 242 == all green, meaning we solve it immediately
          valueForThisGuess += 1;
        } else {
          valueForThisGuess += group.length * (1 + getOptimal(group).value);
        }
      }
      valueForThisGuess /= solnSet.length;
      if (valueForThisGuess < optimal.value) {
        optimal.guesses.length = 0;
        optimal.guesses.push(guess);
        optimal.value = valueForThisGuess;
      } else if (valueForThisGuess === optimal.value) {
        optimal.guesses.push(guess);
      }
    }
  }

  MEMO.set(hash, optimal);
  return optimal;
}

function splitIntoGroups(guess: number, solnSet: number[]) {
  const groups: Map<number, number[]> = new Map();
  for (const soln of solnSet) {
    const pattern = getPattern(guess, soln);
    if (groups.has(pattern)) {
      groups.get(pattern)!.push(soln);
    } else {
      groups.set(pattern, [soln]);
    }
  }
  return groups;
}

interface Optimal {
  guesses: number[], value: number,
}

/*
 * cyrb53 (c) 2018 bryc (github.com/bryc)
 * A fast and simple hash function with decent collision resistance.
 * Largely inspired by MurmurHash2/3, but with a focus on speed/simplicity.
 * Public domain. Attribution appreciated.
 */
function cyrb53(arr: number[]) {
  let h1 = 0xdeadbeef, h2 = 0x41c6ce57;
  for (let i = 0, n: number; i < arr.length; i++) {
    n = arr[i];
    h1 = Math.imul(h1 ^ n, 2654435761);
    h2 = Math.imul(h2 ^ n, 1597334677);
  }
  h1 = Math.imul(h1 ^ (h1 >>> 16), 2246822507) ^
      Math.imul(h2 ^ (h2 >>> 13), 3266489909);
  h2 = Math.imul(h2 ^ (h2 >>> 16), 2246822507) ^
      Math.imul(h1 ^ (h1 >>> 13), 3266489909);
  return 4294967296 * (2097151 & h2) + (h1 >>> 0);
};

main();
