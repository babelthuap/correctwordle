import os from 'os';
import {isMainThread, parentPort, threadId, Worker} from 'worker_threads';

import guessList from './guess_list.json';
import {getPattern, init} from './pattern';
import solutionList from './solution_list.json';

const wordList = [...solutionList, ...guessList];

async function main() {
  init(solutionList, guessList);

  if (isMainThread) {
    console.time('main');

    const numCpus = os.cpus().length;
    console.log('num cpus:', numCpus);

    let globalOpt: Optimal = {guesses: [], value: Infinity};
    const promises = [];
    let guessIdx = 0;
    for (let i = 0; i < numCpus; i++) {
      promises.push(new Promise<void>((res) => {
        const worker = new Worker(__filename);
        worker.postMessage(guessIdx++);
        worker.addListener('message', (localOpt: Optimal) => {
          if (localOpt.value < globalOpt.value) {
            globalOpt = localOpt;
          }
          if (guessIdx < wordList.length) {
            worker.postMessage(guessIdx++);
          } else {
            worker.postMessage(undefined);
            worker.removeAllListeners();
            res();
          }
        });
      }));
    }

    await Promise.all(promises);
    console.log(
        'globalOpt:', globalOpt.value, 'for',
        globalOpt.guesses.map(i => wordList[i]));

    console.timeEnd('main');
  } else {

    // Worker thread
    parentPort?.addListener('message', (guessIdx) => {
      if (guessIdx === undefined) {
        parentPort?.removeAllListeners();
      } else {
        console.log(`Worker ${threadId} calculating ${wordList[guessIdx]}`);
        const solnSet = new Array(50).fill(0).map((_, i) => i);
        const optimal = getOptimal(solnSet, guessIdx);
        parentPort?.postMessage(optimal);
      }
    });
  }
}

const MEMO: Map<number, Optimal> = new Map();
function getOptimal(solnSet: number[], guessIdx?: number): Optimal {
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

  // If not on the top level, try guesses in the solution set first
  if (guessIdx === undefined) {
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
  }

  // The best we can do for non-solutions is 2. If we haven't already reached
  // that, then try all possible guesses.
  if (optimal.value > 2) {
    const low = (guessIdx !== undefined ? guessIdx : 0);
    const high = (guessIdx !== undefined ? guessIdx + 1 : wordList.length);
    for (let guess = low; guess < high; guess++) {
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
