package com.thrallinone;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Which reminder a sidebar preview button shows. */
@Getter
@RequiredArgsConstructor
enum PreviewReminder
{
	RECAST("Recast", "the Recast Thrall box, and send Notify Me, Recast Flash and Recast Sound"),
	SPELLBOOK("Spellbook", "the Not on the Arceuus Spellbook warning"),
	BOOK("Book", "the No Book of the Dead warning"),
	RUNES("Runes", "the No Thrall Runes warning"),
	ALL("All", "every reminder, and send Notify Me, Recast Flash and Recast Sound");

	/** Button text. */
	private final String name;
	/** What the button shows, for its tooltip. */
	private final String shows;

	boolean shows(PreviewReminder reminder)
	{
		return this == reminder || this == ALL;
	}
}
