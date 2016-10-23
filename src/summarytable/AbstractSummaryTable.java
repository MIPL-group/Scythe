package summarytable;

import forward_enumeration.context.EnumContext;
import backward_inference.MappingInference;
import sql.lang.Table;
import sql.lang.ast.filter.EmptyFilter;
import util.Pair;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Created by clwang on 3/26/16.
 */
public abstract class AbstractSummaryTable {

    public int primitiveBitVecFilterCount = 0;
    public int primitiveSynFilterCount = 0;

    public boolean allfiltersEnumerated = false;
    public int totalBitVecFiltersCount = 0;

    // this determines that the method "combining filters" will only generate filters of length 2
    static int maxFilterLength = 2;
    static public final BiFunction<Boolean, Boolean, Boolean> mergeFunction = (x, y) -> x && y;

    abstract public Table getBaseTable();
    abstract public Set<BVFilter> instantiateAllFilters();
    abstract public int getPrimitiveFilterNum();
    abstract public int compoundPrimitiveFilterCount();
    abstract public int compoundFilterCount();
    abstract public List<Integer> getTableRightIndexBoundries();
    abstract public List<Table> getAllPrimitiveBaseTables();
    // calculate the cost of a symbolic filter
    abstract public double estimatePrimitiveSymFilterCost(BVFilter sf);

    // Given a list of target symbolic filters to decompose, we will generate what are their decompositions
    // For each one, a tree will be generated and the tree represent how the symbolic is built from ground.
    abstract public Map<BVFilter, List<BVFilterCompTree>> batchGenDecomposition(Set<BVFilter> targets);

    // abstract public Pair<Set<SymbolicFilter>, FilterLinks> lastStageInstantiateAllFilters(Set<SymbolicFilter> targetFilters);

    // the function that encodes primitive filters into bit vector filters
    // encoded filters are stored in the summary tables
    abstract void encodePrimitiveFilters(EnumContext ec);

    public void emitInstantiateAllTables(
            EnumContext ec,
            BiConsumer<AbstractSummaryTable, BVFilter> f) {

        this.encodePrimitiveFilters(ec);

        Set<BVFilter> filters = this.instantiateAllFilters();

        for (BVFilter spf : filters) {
            f.accept(this, spf);
        }
    }

    public abstract void emitFinalVisitAllTables(
            MappingInference mi,
            EnumContext ec,
            BiConsumer<AbstractSummaryTable, BVFilter> f);

    public List<Table> instantiateAllTables(EnumContext ec) {
        this.encodePrimitiveFilters(ec);

        Set<BVFilter> filters = this.instantiateAllFilters();
        Table table = this.getBaseTable();

        List<Table> instantiatedTables = new ArrayList<>();
        for (BVFilter spf : filters) {
            Table t = table.duplicate();
            t.getContent().clear();
            for (Integer i : spf.getFilterRep()) {
                t.getContent().add(table.getContent().get(i).duplicate());
            }
            if (! t.equals(table))
                t.updateName(Table.AssignNewName());
            instantiatedTables.add(t);
        }
        return instantiatedTables.stream().filter(t -> t.getContent().size() > 0).collect(Collectors.toList());
    }

    public List<Pair<Table, BVFilter>> instantiatedAllTablesWithBVFilters(EnumContext ec) {

        this.encodePrimitiveFilters(ec);

        Set<BVFilter> filters = this.instantiateAllFilters();
        Table table = this.getBaseTable();

        List<Pair<Table, BVFilter>> result = new ArrayList<>();
        for (BVFilter sf : filters) {
            Table t = table.duplicate();
            t.getContent().clear();
            for (Integer i : sf.getFilterRep()) {
                t.getContent().add(table.getContent().get(i).duplicate());
            }
            if (t.getContent().size() > 0) {
                // if sf is an empty filter, then the filtered table is just the original table
                if (! sf.isEmptyFilter())
                    t.updateName(Table.AssignNewName());
                result.add(new Pair<>(t, sf));
            }
        }

        return result;
    }

    // Generate the set of conjunctive filters that can be derived from the set of provided primitive filters.
    public static Set<BVFilter> genConjunctiveFilters(
            AbstractSummaryTable ast,
            List<BVFilter> primitives) {

        Set<BVFilter> filters = new HashSet<>();

        for (int i = 0; i < primitives.size(); i ++) {
            for (int j = i + 1; j < primitives.size(); j ++) {
                BVFilter mergedFilter = BVFilter.mergeFilterConj(
                        primitives.get(i),
                        primitives.get(j));
                filters.add(mergedFilter);
            }
        }

        // this is used to make sure that the empty filter is added, even the input set is 0
        filters.add(BVFilter.genSymbolicFilter(ast.getBaseTable(), new EmptyFilter()));
        return filters;
    }

    // Generate the set of disjunctive filters that can be derived from the set of provided primitive filters.
    public static Set<BVFilter> genDisjunctiveFilters(
            AbstractSummaryTable ast,
            List<BVFilter> primitives) {
        Set<BVFilter> filters = new HashSet<>();
        for (int i = 0; i < primitives.size(); i ++) {
            for (int j = i + 1; j < primitives.size(); j ++) {
                BVFilter mergedFilter = BVFilter.mergeFilterDisj(
                        primitives.get(i),
                        primitives.get(j));
                filters.add(mergedFilter);
            }
        }
        // this is used to make sure that the empty filter is added
        filters.add(BVFilter.genSymbolicFilter(ast.getBaseTable(), new EmptyFilter()));
        return filters;
    }

    // index = sum_{k=1}^{i} (n-k) + (j-i)
    // TODO: use a formula to simplify this
    protected Pair<Integer, Integer> inverseFilterIndex(int n, int index) {
        for (int i = 0; i < n; i ++) {
            for (int j = i + 1; j < n; j ++) {
                int ind =  n * i - i * (i + 1) / 2 + (j - i) - 1;
                if (ind == index)
                    return new Pair<>(i, j);
            }
        }
        return new Pair<>(-1, -1);
    }

    // checks whether sf contains at least one filter in the target.
    protected boolean fullyContainedAnElement(
            BVFilter sf, Set<BVFilter> target) {
        for (BVFilter f : target) {
            if (sf.fullyContained(f)) {
                return true;
            }
        }
        return false;
    }
    // checks whether sf contains at least one filter in the target.
    protected boolean fullyContainedARange(
            BVFilter sf, List<List<Set<Integer>>> target) {
        for (List<Set<Integer>> t : target) {
            if (sf.rangeFullyContained(t)) {
                return true;
            }
        }
        return false;
    }
    // checks whether sf is contained by at least one filter in the target.
    protected boolean fullyContainedByAnElement(
            BVFilter sf, Set<BVFilter> target) {
        for (BVFilter f : target) {
            if (f.fullyContained(sf)) {
                return true;
            }
        }
        return false;
    }

}
