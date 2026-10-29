// Chapter 20, Challenge 3: benchmark programs for the clox hash table.
//
// Five access patterns, chosen because a hash table's performance depends
// on how it's used, not just how big it gets:
//
//   1. Sequential unique inserts
//        Baseline insert cost as the table grows and resizes.
//
//   2. 100% hit-rate lookups
//        Baseline cost of a successful probe.
//
//   3. 100% miss-rate lookups
//        Worst case: a miss must walk every occupied/tombstoned slot in
//        the probe chain before reaching a true empty one.
//
//   4. Same-key churn
//        Repeatedly insert/delete the SAME set of keys. Deleted slots
//        become tombstones, and the next insert of that same key reuses
//        that exact slot, so this should stay cheap forever.
//
//   5. Distinct-key churn
//        Repeatedly insert/delete a DIFFERENT set of keys each round.
//        Tombstones from round N are never reused by round N+1's keys,
//        and tableDelete() never decrements count, so tombstones can
//        only ever be flushed by a capacity resize.
//
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#include "object.h"
#include "table.h"
#include "value.h"
#include "vm.h"

static ObjString** makeKeys(int count, const char* prefix) {
  ObjString** keys = malloc(sizeof(ObjString*) * count);
  char buf[64];
  for (int i = 0; i < count; i++) {
    int len = snprintf(buf, sizeof(buf), "%s%d", prefix, i);
    keys[i] = copyString(buf, len);
  }
  return keys;
}

static double elapsedMs(clock_t start, clock_t end) {
  return (double)(end - start) * 1000.0 / CLOCKS_PER_SEC;
}

static void benchInsert(int n) {
  ObjString** keys = makeKeys(n, "key");
  Table table;
  initTable(&table);

  clock_t start = clock();
  for (int i = 0; i < n; i++) {
    tableSet(&table, keys[i], NUMBER_VAL(i));
  }
  clock_t end = clock();

  printf("  insert %7d unique keys:        %8.3f ms  (cap %d)\n",
         n, elapsedMs(start, end), table.capacity);

  freeTable(&table);
  free(keys);
}

static void benchLookupHit(int n) {
  ObjString** keys = makeKeys(n, "key");
  Table table;
  initTable(&table);
  for (int i = 0; i < n; i++) tableSet(&table, keys[i], NUMBER_VAL(i));

  Value out;
  clock_t start = clock();
  for (int i = 0; i < n; i++) {
    tableGet(&table, keys[i], &out);
  }
  clock_t end = clock();

  printf("  lookup  %7d keys (100%% hit):   %8.3f ms\n",
         n, elapsedMs(start, end));

  freeTable(&table);
  free(keys);
}

static void benchLookupMiss(int n) {
  ObjString** keys = makeKeys(n, "key");
  ObjString** missKeys = makeKeys(n, "absent");
  Table table;
  initTable(&table);
  for (int i = 0; i < n; i++) tableSet(&table, keys[i], NUMBER_VAL(i));

  Value out;
  clock_t start = clock();
  for (int i = 0; i < n; i++) {
    tableGet(&table, missKeys[i], &out);
  }
  clock_t end = clock();

  printf("  lookup  %7d keys (100%% miss):  %8.3f ms\n",
         n, elapsedMs(start, end));

  freeTable(&table);
  free(keys);
  free(missKeys);
}

static void benchChurnSameKeys(int n, int rounds) {
  ObjString** keys = makeKeys(n, "churnSame");
  Table table;
  initTable(&table);

  clock_t start = clock();
  for (int r = 0; r < rounds; r++) {
    for (int i = 0; i < n; i++) tableSet(&table, keys[i], NUMBER_VAL(i));
    for (int i = 0; i < n; i++) tableDelete(&table, keys[i]);
  }
  clock_t end = clock();

  printf("  same-key churn: %d keys x %4d rounds: %8.3f ms  (cap %d, count %d)\n",
         n, rounds, elapsedMs(start, end), table.capacity, table.count);

  freeTable(&table);
  free(keys);
}

static void benchChurnDistinctKeys(int perRound, int rounds) {
  Table table;
  initTable(&table);

  double roundMs[8];
  int sampleRounds = rounds < 8 ? rounds : 8;
  int sampleEvery = rounds / sampleRounds;

  clock_t start = clock();
  for (int r = 0; r < rounds; r++) {
    char prefix[32];
    snprintf(prefix, sizeof(prefix), "churnR%d_", r);
    ObjString** keys = makeKeys(perRound, prefix);

    clock_t roundStart = clock();
    for (int i = 0; i < perRound; i++) tableSet(&table, keys[i], NUMBER_VAL(i));
    for (int i = 0; i < perRound; i++) tableDelete(&table, keys[i]);
    clock_t roundEnd = clock();

    if (r % sampleEvery == 0 && r / sampleEvery < sampleRounds) {
      roundMs[r / sampleEvery] = elapsedMs(roundStart, roundEnd);
    }

    free(keys);
  }
  clock_t end = clock();

  printf("  distinct-key churn: %d keys x %4d rounds: %8.3f ms total  (cap %d, count %d)\n",
         perRound, rounds, elapsedMs(start, end), table.capacity, table.count);
  printf("    per-round cost over time (tombstones accumulating):\n");
  for (int i = 0; i < sampleRounds; i++) {
    printf("      round %5d: %.4f ms\n", i * sampleEvery, roundMs[i]);
  }

  freeTable(&table);
}

int main() {
  initVM();

  printf("=== clox hash table benchmarks (Chapter 20, Challenge 3) ===\n\n");

  printf("-- 1. sequential unique inserts --\n");
  benchInsert(100);
  benchInsert(1000);
  benchInsert(10000);
  benchInsert(100000);

  printf("\n-- 2. successful lookups (100%% hit) --\n");
  benchLookupHit(100);
  benchLookupHit(1000);
  benchLookupHit(10000);
  benchLookupHit(100000);

  printf("\n-- 3. failed lookups (100%% miss) --\n");
  benchLookupMiss(100);
  benchLookupMiss(1000);
  benchLookupMiss(10000);
  benchLookupMiss(100000);

  printf("\n-- 4. same-key churn (tombstone slots get reused) --\n");
  benchChurnSameKeys(100, 20000);
  benchChurnSameKeys(1000, 2000);

  printf("\n-- 5. distinct-key churn (tombstones accumulate, never reused) --\n");
  benchChurnDistinctKeys(50, 20000);

  freeVM();
  return 0;
}
