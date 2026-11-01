package dev.onepieceapi.contentservice.persistence.entity;

import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * How an ordered list of child rows with an id of their own is rewritten (implementation
 * plan of the Character, D14): each position keeps its row, rewritten in place; only the
 * end of the list grows or shrinks. Replacing the rows would insert a position before the
 * old row holding it is deleted, against the unique position of the table.
 */
@UtilityClass
class PositionalRows {

	<R, W> void rewrite(List<R> rows, List<W> written, Function<W, R> create, BiConsumer<R, W> rewrite) {
		for (int index = 0; index < written.size(); index++) {
			if (index < rows.size()) {
				rewrite.accept(rows.get(index), written.get(index));
			}
			else {
				rows.add(create.apply(written.get(index)));
			}
		}
		while (rows.size() > written.size()) {
			rows.removeLast();
		}
	}

}
