package com.thrallinone;

/**
 * The one casts warning, shown the same way in game (the amber box) and in
 * the sidebar (the triangle, its words, and the dot on the button): fewer
 * casts than Warn Below, while on the Arceuus spellbook with the Book of the
 * Dead. Without those two a player isn't using thralls right now, and would
 * be warned about runes they never meant to bring. Warn Below 0 turns it off.
 */
enum RuneWarning
{
	NONE,
	LOW,
	OUT;

	/**
	 * @param casts the casts the runes on us pay for, counting a staff or tome in the inventory as worn
	 * ({@link CastsCounter#castsWithCarried}); -1 before it's counted
	 * @param warnBelow the Warn Below setting
	 * @param canSummon on the Arceuus spellbook with the Book of the Dead
	 */
	static RuneWarning of(int casts, int warnBelow, boolean canSummon)
	{
		if (!canSummon || casts < 0 || casts >= warnBelow)
		{
			return NONE;
		}
		return casts == 0 ? OUT : LOW;
	}
}
