package net.sourceforge.opencamera;

import org.junit.experimental.categories.Categories;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/** Tests related to taking photos; note that tests to do with photo mode that don't take photos are still part of MainInstrumentedTests.
 */

@RunWith(Categories.class)
@Categories.IncludeCategory(net.sourceforge.opencamera.PhotoTests.class)
@Suite.SuiteClasses({net.sourceforge.opencamera.InstrumentedTest.class})
public class PhotoInstrumentedTests {}
