// Read-only loader/relocation test: no guest instructions, constructors or services run.
// The optional expected-pairs file is generated locally from the user's ExeFS and never committed.
#include "rt.h"
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>

void rt_fatal_trace(void) { abort(); }
void plat_commit(uint64_t address, uint64_t size) {
    void *p = mmap((void *)address, size, PROT_READ | PROT_WRITE,
                  MAP_PRIVATE | MAP_ANONYMOUS | MAP_FIXED_NOREPLACE, -1, 0);
    if (p == MAP_FAILED) { perror("mmap guest module"); exit(1); }
}
HostFn hle_lookup_impl(const char *name) { (void)name; return NULL; }
uint64_t hle_register(const char *name, HostFn fn) {
    (void)fn;
    static char *names[4096];
    static unsigned count;
    for (unsigned i = 0; i < count; i++) if (!strcmp(names[i], name)) return HLE_BASE + 16ull * i;
    if (count == 4096) abort();
    names[count] = strdup(name);
    return HLE_BASE + 16ull * count++;
}
int main(int argc, char **argv) {
    if (argc != 3) { fprintf(stderr, "usage: loader_probe <exefs> <expected-relative-pairs>\n"); return 2; }
    loader_load(argv[1]);
    if (g_nmodules != 3 || g_game_version != 143 || strcmp(g_modules[2].name, "subsdk0")) return 1;
    FILE *f = fopen(argv[2], "rb");
    if (!f) { perror("expected pairs"); return 1; }
    uint64_t pairs[2], tested = 0;
    while (fread(pairs, sizeof pairs, 1, f) == 1) {
        bool in_module = false;
        for (int i = 0; i < g_nmodules; i++)
            if (pairs[0] >= g_modules[i].base && pairs[0] + 8 <= g_modules[i].base + g_modules[i].size) in_module = true;
        if (!in_module || *(uint64_t *)pairs[0] != pairs[1]) {
            fprintf(stderr, "relocation mismatch at %llx\n", (unsigned long long)pairs[0]); return 1;
        }
        tested++;
    }
    if (ferror(f) || !feof(f) || ftell(f) % sizeof pairs || !tested) return 1;
    fclose(f);
    printf("PASS: three modules loaded, %llu relative relocations matched; guest code NOT executed\n", (unsigned long long)tested);
    return 0;
}
