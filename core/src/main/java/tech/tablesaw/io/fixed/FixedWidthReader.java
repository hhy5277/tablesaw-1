/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tech.tablesaw.io.fixed;

import com.google.common.io.CharStreams;
import com.univocity.parsers.common.AbstractParser;
import com.univocity.parsers.fixed.FixedWidthFormat;
import com.univocity.parsers.fixed.FixedWidthParser;
import com.univocity.parsers.fixed.FixedWidthParserSettings;
import org.apache.commons.lang3.tuple.Pair;
import tech.tablesaw.api.ColumnType;
import tech.tablesaw.api.Table;
import tech.tablesaw.io.FileReader;
import tech.tablesaw.io.TableBuildingUtils;

import javax.annotation.concurrent.Immutable;
import java.io.IOException;
import java.io.Reader;
import java.util.List;

@Immutable
public class FixedWidthReader extends FileReader {

    /**
     * Constructs a FixedWidthReader
     */
    public FixedWidthReader() {
        super();
    }

    /**
     * Constructs a FixedWidthReader with the given list of ColumnTypes
     * <p>
     * These are the only types that the FixedWidthReader can detect and parse
     */
    public FixedWidthReader(List<ColumnType> typeDetectionList) {
        super(typeDetectionList);
    }

    /**
     * Determines column types if not provided by the user
     * Reads all input into memory unless File was provided
     */
    private Pair<Reader, ColumnType[]> getReaderAndColumnTypes(FixedWidthReadOptions options) throws IOException {
        ColumnType[] types = options.columnTypes();
        byte[] bytesCache = null;

        if (types == null) {
            Reader reader = TableBuildingUtils.createReader(options, bytesCache);
            if (options.file() == null) {
                bytesCache = CharStreams.toString(reader).getBytes();
                // create a new reader since we just exhausted the existing one
                reader = TableBuildingUtils.createReader(options, bytesCache);
            }
            types = detectColumnTypes(reader, options);
        }

        return Pair.of(TableBuildingUtils.createReader(options, bytesCache), types);
    }

    public Table read(FixedWidthReadOptions options) throws IOException {
        return read(options, false);
    }

    private Table read(FixedWidthReadOptions options, boolean headerOnly) throws IOException {
        Pair<Reader, ColumnType[]> pair = getReaderAndColumnTypes(options);
        Reader reader = pair.getLeft();
        ColumnType[] types = pair.getRight();

        FixedWidthParser parser = fixedWidthParser(options);

        try {
            return parseRows(options, headerOnly, reader, types, parser);
        } finally {
            if (options.reader() == null) {
                // if we get a reader back from options it means the client opened it, so let the client close it
                // if it's null, we close it here.
                parser.stopParsing();
                reader.close();
            }
        }
    }

    /**
     * Returns a string representation of the column types in file {@code fixed widthFilename},
     * as determined by the type-detection algorithm
     * <p>
     * This method is intended to help analysts quickly fix any erroneous types, by printing out the types in a format
     * such that they can be edited to correct any mistakes, and used in an array literal
     * <p>
     * For example:
     * <p>
     * LOCAL_DATE, // 0     date
     * SHORT,      // 1     approval
     * STRING,     // 2     who
     * <p>
     * Note that the types are array separated, and that the index position and the column name are printed such that
     * they would be interpreted as comments if you paste the output into an array:
     * <p>
     *
     * @throws IOException if file cannot be read
     */
    public String printColumnTypes(FixedWidthReadOptions options) throws IOException {

        Table structure = read(options, true).structure();

        return getTypeString(structure);
    }

    /**
     * Estimates and returns the type for each column in the delimited text file {@code file}
     * <p>
     * The type is determined by checking a sample of the data in the file. Because only a sample of the data is
     * checked,
     * the types may be incorrect. If that is the case a Parse Exception will be thrown.
     * <p>
     * The method {@code printColumnTypes()} can be used to print a list of the detected columns that can be
     * corrected and
     * used to explicitly specify the correct column types.
     */
    public ColumnType[] detectColumnTypes(Reader reader, FixedWidthReadOptions options) {

        boolean header = options.header();
        int linesToSkip = header ? 1 : 0;

        AbstractParser<?> parser = fixedWidthParser(options);

        try {
            return getTypes(reader, options, linesToSkip, parser);
        } finally {
            parser.stopParsing();
            // we don't close the reader since we didn't create it
        }
    }

    private FixedWidthParser fixedWidthParser(FixedWidthReadOptions options) {
        FixedWidthParserSettings settings = new FixedWidthParserSettings();

        if (options.columnSpecs() != null) {
            settings = new FixedWidthParserSettings(options.columnSpecs());
        }
        settings.setFormat(fixedWidthFormat(options));
        if (options.skipTrailingCharsUntilNewline()) {
            settings.setSkipTrailingCharsUntilNewline(options.skipTrailingCharsUntilNewline());
        }
        if (options.maxNumberOfColumns() != null) {
            settings.setMaxColumns(options.maxNumberOfColumns());
        }
        if (options.recordEndsOnNewline()) {
            settings.setRecordEndsOnNewline(true);
        }
        return new FixedWidthParser(settings);
    }

    private FixedWidthFormat fixedWidthFormat(FixedWidthReadOptions options) {
        FixedWidthFormat format = new FixedWidthFormat();
        if (options.padding() != ' ') {
            format.setPadding(options.padding());
        }
        if (options.lookupWildcard() != '?') {
            format.setLookupWildcard(options.lookupWildcard());
        }
        if (options.lineEnding() != null) {
            format.setLineSeparator(options.lineEnding());
        }
        return format;
    }

}
