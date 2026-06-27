package net.sourceforge.opencamera;

import org.junit.experimental.categories.Categories;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/** Tests that don't fit into another of the Test suites.
 */

@RunWith(Categories.class)
@Categories.IncludeCategory(net.sourceforge.opencamera.MainTests.class)
@Suite.SuiteClasses({net.sourceforge.opencamera.InstrumentedTest.class})
public class MainInstrumentedTests {}
