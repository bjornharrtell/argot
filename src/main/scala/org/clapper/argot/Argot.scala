/*
  ---------------------------------------------------------------------------
  This software is released under a BSD license, adapted from
  http://opensource.org/licenses/bsd-license.php

  Copyright (c) 2010, Brian M. Clapper
  All rights reserved.

  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions are
  met:

  * Redistributions of source code must retain the above copyright notice,
    this list of conditions and the following disclaimer.

  * Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in the
    documentation and/or other materials provided with the distribution.

  * Neither the names "clapper.org", "Argot", nor the names of its
    contributors may be used to endorse or promote products derived from
    this software without specific prior written permission.

  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
  IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
  THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
  PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
  EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
  PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
  ---------------------------------------------------------------------------
*/

/**
 * Argot is a command-line argument parsing API for Scala.
 */
package org.clapper.argot

import scala.reflect.Manifest
import scala.collection.mutable.{Map => MutableMap,
                                 LinkedHashMap,
                                 LinkedHashSet}
import scala.util.matching.Regex
import scala.annotation.tailrec

/**
 * Base trait for all option and parameter classes, `CommandLineArgument`
 * contains comment methods and values.
 *
 * @tparam T  the type associated with the argument
 */
trait CommandLineArgument[T]
{
    /**
     * The `ArgotParser` instance that owns this object.
     */
    val parent: ArgotParser

    /**
     * The argument's description, displayed in the usage message.
     */
    val description: String

    /**
     * Whether or not the argument has an associated value. For instance,
     * parameters have values, and non-flag options have values. Flag options,
     * however, do not.
     */
    val hasValue: Boolean

    /**
     * Displayable name for the argument, used in the usage message.
     *
     * @return the name
     */
    def name: String

    /**
     * The standard `equals()` method.
     *
     * @param o  some other object
     *
     * @return `true` if the other object is the same class and is equivalent,
     *         `false` if not.
     */
    override def equals(o: Any): Boolean =
    {
        o match
        {
            case that: CommandLineArgument[_] =>
                (this.getClass == that.getClass) && (this.key == that.key)
            case _ =>
                false
        }
    }

    /**
     * Calculate the hash code for the object. The default implementation
     * returns the hash code of the key.
     *
     * @return the hash code
     *
     * @see #key
     */
    override def hashCode = key.hashCode

    /**
     * Return an object that represents the key for this parameter, suitable
     * for hashing, sorting, etc.
     *
     * @return the key
     */
    protected def key: Any
}

/**
 * The `HasValue` trait is mixed into option and parameter classes that
 * support one or mor associated values of type `T`.
 *
 * @tparam T  the value type
 *
 * @see SingleValueArg
 * @see MultiValueArg
 */
trait HasValue[T] extends CommandLineArgument[T]
{
    /**
     * Always `true`, indicating that `HasValue` classes always have an
     * associated value.
     */
    val hasValue: Boolean = true

    /**
     * Whether or not the class supports multiple values (e.g., a sequence)
     * or just one.
     */
    val supportsMultipleValues: Boolean

    /**
     * Method that converts a string value to type `T`. Should throw
     * `ArgotConversionException` on error.
     *
     * @param s  the string to convert
     *
     * @return the converted result
     *
     * @throws ArgotConversionException  conversion error
     */
    def convertString(s: String): T

    /**
     * Store a converted value.
     *
     * @param v  the value, of type `T`
     */
    def storeValue(v: T): Unit

    /**
     * Given a string value, convert the value to type `T` by calling
     * `convert()`, then store it by calling `storeValue()`.
     *
     * @param s  the string to convert
     *
     * @throws ArgotConversionException  conversion error
     */
    def setFromString(s: String) = storeValue(convertString(s))
}

/**
 * `SingleValueArg` is a refinement of the `HasValue` trait, specifically
 * for arguments (options or parameters) that take only a single value.
 * This trait exists primarily as a place for shared logic and values
 * for the option- and parameter-specific subclasses.
 *
 * @tparam T  the value type
 *
 * @see SingleValueOption
 * @see SingleValueParameter
 */
trait SingleValueArg[T] extends HasValue[T]
{
    val supportsMultipleValues = false
    var value: Option[T] = None

    def get = value.get
    def getOrElse(default: T) = value.getOrElse(default)
    def storeValue(v: T): Unit = value = Some(v)
}

/**
 * `MultiValueArg` is a refinement of the `HasValue` trait, specifically
 * for arguments (options or parameters) that take multiple values of type
 * `T`. Each instance of the parameter on the command line adds to the
 * sequence of values in associated `MultiValueArg` object.
 *
 * This trait exists primarily as a place for shared logic and values
 * for the option- and parameter-specific subclasses.
 *
 * @tparam T  the value type
 *
 * @see MultiValueOption
 * @see MultiValueParameter
 */
trait MultiValueArg[T] extends HasValue[T]
{
    val supportsMultipleValues = true
    var value: Seq[T] = Seq.empty[T]

    def get = value
    def storeValue(v: T): Unit = value = value.toList ::: List(v)
}

/**
 * `CommandLineOption` is the base trait for all option classes.
 *
 * @tparam T  the value type
 *
 * @see SingleValueOption
 * @see MultiValueOption
 */
trait CommandLineOption[T] extends CommandLineArgument[T]
{
    /**
     * List of option names, both long (multi-character) and short
     * (single-character).
     */
    val names: List[String]

    /**
     * Return a suitable name for the option. The returned name will
     * have a "-" or "--" prefix, depending on whether it's long or short.
     * It will be based on the first option in the list of option names.
     *
     * @return the option name
     */
    def name = names(0) match
    {
        case s: String if s.length > 1  => "--" + s
        case s: String if s.length == 1 => "-" + s
    }

    /**
     * Get a printable name for this object.
     *
     * @return the printable name
     */
    override def toString = "option " + name

    /**
     * Return an object that represents the key for this parameter, suitable
     * for hashing, sorting, etc. They key for a command line option is the
     * result of calling `name()`.
     *
     * @return the key
     */
    protected def key = name
}

/**
 * A trait that's mixed into non-flag options.
 */
trait NonFlagOption
{
    /**
     * All non-flag options have to have a placeholder name for the value,
     * used in generating the usage message.
     */
    val valueName: String
}

/**
 * Class for an option that takes a single value.
 *
 * @tparam  the type of the converted option value
 *
 * @param parent      the parent parser instance that owns the option
 * @param names       the list of names the option is known by
 * @param valueName   the placeholder name for the option's value, for the
 *                    usage message
 * @param description textual description of the option
 * @param convert     a function that will convert a string value for the
 *                    option to an appropriate value of type `T`.
 */
class SingleValueOption[T](val parent: ArgotParser,
                           val names: List[String],
                           val valueName: String,
                           val description: String,
                           val convert: (String, SingleValueOption[T]) => T)
extends CommandLineOption[T] with SingleValueArg[T] with NonFlagOption
{
    require ((names != Nil) && (! names.exists(_.length == 0)))

    def convertString(s: String): T = convert(s, this)
}

/**
 * Class for an option that takes a multiple values. Each instance of the
 * option on the command line adds to the sequence of values associated
 * with the option.
 *
 * @tparam  the type of the converted option value
 *
 * @param parent      the parent parser instance that owns the option
 * @param names       the list of names the option is known by
 * @param valueName   the placeholder name for the option's value, for the
 *                    usage message
 * @param description textual description of the option
 * @param convert     a function that will convert a string value for the
 *                    option to an appropriate value of type `T`.
 */
class MultiValueOption[T](val parent: ArgotParser,
                          val names: List[String],
                          val valueName: String,
                          val description: String,
                          val convert: (String, MultiValueOption[T]) => T)
extends CommandLineOption[T] with MultiValueArg[T] with NonFlagOption
{
    require ((names != Nil) && (! names.exists(_.length == 0)))

    def convertString(s: String): T = convert(s, this)
}

/**
 * Class for a flag. A flag option consists of a set of names that enable
 * the flag (e.g.,, set it to true) if present on the command line, and a set
 * of names that disable the flag (e.g., set it to false) if present on the
 * command line. The type of flag option can be anything, but is generally
 * boolean.
 *
 * @tparam  the underlying value type
 *
 * @param parent      the parent parser instance that owns the option
 * @param namesOn     list of names (short or long) that toggle the value on
 * @param namesOff    list of names (short or long) that toggle the value off
 * @param default     default value
 * @param description textual description of the option
 * @param convert     a function that takes a boolean value and maps it to
 *                    the appropriate value to store as the option's value.
 */
/**
 * @Param convert  conversion function, which can be used to map the
 *                 "on" or "off" state to another value type. It takes
 *                 a `true` (on) or `false` value as the first parameter.
 */
class FlagOption[T](val parent: ArgotParser,
                    namesOn: List[String],
                    namesOff: List[String],
                    default: T,
                    val description: String,
                    val convert: (Boolean, FlagOption[T]) => T)
extends CommandLineOption[T]
{
    val supportsMultipleValues = false
    val hasValue: Boolean = true
    var value: T = default

    private val shortNamesOnSet = namesOn.filter(_.length == 1).toSet
    private val shortNamesOffSet = namesOff.filter(_.length == 1).toSet
    private val longNamesOnSet = namesOn.filter(_.length > 1).toSet
    private val longNamesOffSet = namesOff.filter(_.length > 1).toSet

    require (wellDefined)

    val names = namesOn ::: namesOff

    /**
     * Called when the option is set (i.e., when one of the "on" names is
     * seen on the command line). Subclasses may override this method.
     * The default version calls `convert()` with a `true`, and stores the
     * result in `value`.
     */
    def set: Unit = value = convert(true, this)

    /**
     * Called when the option to unset (i.e., when one of the "off" names is
     * seen on the command line). Subclasses may override this method.
     * The default version calls `convert()` with a `false` and stores the
     * result in `value`.
     */
    def clear: Unit = value = convert(false, this)

    /**
     * Set the value, based on whether the specified option name is an
     * "on" or an "off" name.
     *
     * @param name  the name, without any leading "-" or "--"
     */
    def setByName(name: String): Unit =
    {
        assert(name.length > 0)

        checkValidity(name)

        name.length match
        {
            case 1 => if (shortNamesOnSet contains names(0)) set else clear
            case _ => if (longNamesOnSet contains name) set else clear
        }
    }

    /**
     * Displayable name for the argument, used in the usage message.
     *
     * @return the name
     */
    override def name = namesOn match
    {
        case c :: tail => "-" + c.toString
        case Nil       => "--" + namesOff(0)
    }

    /**
     * Return an object that represents the key for this parameter, suitable
     * for hashing, sorting, etc. They key for a command line option is the
     * result of calling `name()`.
     *
     * @return the key
     */
    override protected def key =
        namesOn.mkString("|") + "!" + namesOff.mkString("|")

    private def wellDefined: Boolean =
    {
        def inBoth(s: String) =
            (((shortNamesOnSet | longNamesOnSet) contains s) &&
             ((shortNamesOffSet | longNamesOffSet) contains s))

        val l = namesOn ::: namesOff
        (l != Nil) && (! l.exists(_.length == 0)) && (! l.exists(inBoth _))
    }

    private def checkValidity(optName: String) =
    {
        if (! ((shortNamesOnSet contains optName) ||
               (shortNamesOffSet contains optName) ||
               (longNamesOnSet contains optName) ||
               (longNamesOffSet contains optName)) )
            throw new ArgotException("(BUG) Flag name \"" + optName +
                                     "\" is neither a short nor a long name " +
                                     "for option \"" + this.name + "\"")
    }
}

/**
 * Base trait for parameter classes
 */
private[argot] trait Parameter[T]
extends CommandLineArgument[T] with HasValue[T]
{
    val convert: (String, Parameter[T]) => T
    val placeholderName: String
    val description: String
    val optional: Boolean

    require (placeholderName.length > 0)

    def name = placeholderName
    def convertString(s: String): T = convert(s, this)
    override def toString = "parameter " + placeholderName
    protected def key = placeholderName
}

/**
 * Class for a non-option parameter that takes a single value.
 *
 * @tparam  the type of the converted parameter value
 *
 * @param parent            the parent parser instance that owns the parameter
 * @param placeholderName   the placeholder name for the parameter's value,
 *                          for the usage message
 * @param description       textual description of the parameter
 * @param optional          whether or not the parameter is optional. Only
 *                          one parameter may be optional, and it must be
 *                          the last one.
 * @param convert           a function that will convert a string value for
 *                          the parameter to an appropriate value of type `T`.
 */
class SingleValueParameter[T](
    val parent: ArgotParser,
    val placeholderName: String,
    val description: String,
    val optional: Boolean,
    val convert: (String, Parameter[T]) => T)
extends Parameter[T] with SingleValueArg[T]

/**
 * Class for a non-option parameter that takes a multiple values. Each
 * instance of the parameter on the command line adds to the sequence of
 * values associated with the parameter.
 *
 * @tparam  the type of the converted parameter value
 *
 * @param parent            the parent parser instance that owns the parameter
 * @param placeholderName   the placeholder name for the parameter's value,
 *                          for the usage message
 * @param description       textual description of the parameter
 * @param optional          whether or not the parameter is optional. Only
 *                          one parameter may be optional, and it must be
 *                          the last one.
 * @param convert           a function that will convert a string value for
 *                          the parameter to an appropriate value of type `T`.
 */
class SingleValueParameters[T](
    val parent: ArgotParser,
    val placeholderName: String,
    val description: String,
    val optional: Boolean,
    val convert: (String, Parameter[T]) => T)
extends Parameter[T] with MultiValueArg[T]

/**
 * Internally used common conversion functions
 */
private object Conversions
{
    implicit def parseInt(s: String, opt: String): Int =
    {
        parseNum[Int](s, s.toInt)
    }

    implicit def parseLong(s: String, opt: String): Long =
    {
        parseNum[Long](s, s.toLong)
    }

    implicit def parseShort(s: String, opt: String): Short =
    {
        parseNum[Short](s, s.toShort)
    }

    implicit def parseFloat(s: String, opt: String): Float =
    {
        parseNum[Float](s, s.toFloat)
    }

    implicit def parseDouble(s: String, opt: String): Double =
    {
        parseNum[Double](s, s.toDouble)
    }

    implicit def parseChar(s: String, opt: String): Char =
    {
        if (s.length != 1)
            throw new ArgotConversionException(
                "Option \"" + opt + "\": " +
                "Cannot parse \"" + s + "\" to a character."
            )
        s(0)
    }

    implicit def parseByte(s: String, opt: String): Byte =
    {
        val num = s.toInt
        if ((num < 0) || (num > 255))
            throw new ArgotConversionException(
                "Option \"" + opt + "\": " + "\"" + s +
                "\" results in a number that is too large for a byte."
            )

        num.toByte
    }

    implicit def parseString(s: String, opt: String): String =
    {
        s
    }

    implicit def parseFlag[Boolean](onOff: Boolean, opt: String): Boolean =
    {
        onOff
    }

    private def parseNum[T](s: String, parse: => T): T =
    {
        try
        {
            parse
        }

        catch
        {
            case e: NumberFormatException =>
                throw new ArgotConversionException(
                    "Cannot convert argument \"" + s + "\" to a number."
                )
        }
    }
}

/**
 * Conversion functions that can be used to satisfy the implicit conversions
 * specified to the various specification functions in the `ArgotParser` class.
 */
object ArgotConverters
{
    implicit def convertInt(s: String, opt: CommandLineArgument[Int]): Int =
    {
        Conversions.parseInt(s, opt.name)
    }

    implicit def convertLong(s: String, opt: CommandLineArgument[Long]): Long =
    {
        Conversions.parseLong(s, opt.name)
    }

    implicit def convertShort(s: String, opt: CommandLineArgument[Short]):
        Short =
    {
        Conversions.parseShort(s, opt.name)
    }

    implicit def convertFloat(s: String, opt: CommandLineArgument[Float]):
        Float =
    {
        Conversions.parseFloat(s, opt.name)
    }

    implicit def convertDouble(s: String, opt: CommandLineArgument[Double]):
        Double =
    {
        Conversions.parseDouble(s, opt.name)
    }

    implicit def convertChar(s: String, opt: CommandLineArgument[Char]): Char =
    {
        Conversions.parseChar(s, opt.name)
    }

    implicit def convertByte(s: String, opt: CommandLineArgument[Byte]): Byte =
    {
        Conversions.parseByte(s, opt.name)
    }

    implicit def convertString(s: String, opt: CommandLineArgument[String]):
        String =
    {
        Conversions.parseString(s, opt.name)
    }

    implicit def convertFlag[Boolean](onOff: Boolean,
                                      opt: FlagOption[Boolean]): Boolean =
    {
        Conversions.parseFlag(onOff, opt.name)
    }

    implicit def convertSeq[T](s: String, opt: MultiValueOption[T])
                              (implicit parse: (String, String) => T):
        Seq[T] =
    {
        opt.value :+ parse(s, opt.name).asInstanceOf[T]
    }
}

/**
 * `ArgotParser` is a command-line parser, with support for single-value and
 * multi-value options, single-value and multi-value parameters, typed value,
 * custom conversions (with suitable defaults), and extensibility.
 *
 * *More to come*
 */
class ArgotParser(programName: String,
                  compactUsage: Boolean = false)
{
    val shortNameMap = MutableMap.empty[Char, CommandLineOption[_]]
    val longNameMap = MutableMap.empty[String, CommandLineOption[_]]
    val allOptions = new LinkedHashMap[String, CommandLineOption[_]]
    val nonFlags = new LinkedHashSet[NonFlagOption]
    val flags = new LinkedHashSet[FlagOption[_]]
    val parameters = new LinkedHashSet[Parameter[_]]

    /**
     * Each string in `names` can be a single character (thus "v" -> "-v")
     * or more than one character (thus "verbose" -> "--verbose").
     */
    def option[T](names: List[String], valueName: String, description: String)
                 (implicit convert: (String, SingleValueOption[T]) => T):
        SingleValueOption[T] =
    {
        val opt = new SingleValueOption[T](this, names, valueName, description,
                                         convert)
        replaceOption(opt)
        nonFlags += opt
        opt
    }

    def option[T](name: String, valueName: String, description: String)
                 (implicit convert: (String, SingleValueOption[T]) => T):
        SingleValueOption[T] =
    {
        option[T](List(name), valueName, description)(convert)
    }

    def multiOption[T](names: List[String],
                       valueName: String,
                       description: String)
                      (implicit convert: (String, MultiValueOption[T]) => T):
        MultiValueOption[T] =
    {
        val opt = new MultiValueOption[T](this, names,
                                          valueName, description, convert)
        replaceOption(opt)
        nonFlags += opt
        opt
    }

    def multiOption[T](name: String, valueName: String, description: String)
                      (implicit convert: (String, MultiValueOption[T]) => T):
        MultiValueOption[T] =
    {
        multiOption[T](List(name), valueName, description)(convert)
    }

    def flag[T](namesOn: List[String],
                namesOff: List[String],
                default: T,
                description: String)
               (implicit convert: (Boolean, FlagOption[T]) => T):
        FlagOption[T] =
    {
        val opt = new FlagOption[T](this, namesOn, namesOff, default,
                                    description, convert)
        replaceOption(opt)
        flags += opt
        opt
    }

    def flag[T](namesOn: List[String],
                default: T,
                description: String)
               (implicit convert: (Boolean, FlagOption[T]) => T):
        FlagOption[T] =
    {
        flag(namesOn, Nil, default, description)(convert)
    }

    def flag[T](name: String, default: T, description: String)
               (implicit convert: (Boolean, FlagOption[T]) => T):
        FlagOption[T] =
    {
        flag[T](List(name), default, description)(convert)
    }

    def parameter[T](placeholderName: String,
                     description: String,
                     optional: Boolean)
                    (implicit convert: (String, Parameter[T]) => T):
        SingleValueParameter[T] =
    {
        val param = new SingleValueParameter[T](this,
                                                placeholderName,
                                                description,
                                                optional,
                                                convert)
        checkOptionalStatus(param, optional)
        checkForMultiParam(param)
        replaceParameter(param)
        param
    }

    def parameters[T](placeholderName: String,
                      description: String,
                      optional: Boolean)
                    (implicit convert: (String, Parameter[T]) => T):
        SingleValueParameters[T] =
    {
        val param = new SingleValueParameters[T](this,
                                                 placeholderName,
                                                 description,
                                                 optional,
                                                 convert)
        checkOptionalStatus(param, optional)
        checkForMultiParam(param)
        replaceParameter(param)
        param
    }

    def checkForMultiParam(param: Parameter[_]) =
    {
        if (parameters.size > 0)
        {
            parameters last match
            {
                case p: SingleValueParameters[_] =>
                    throw new ArgotSpecificationError(
                        "Multi-parameter \"" + p.name + "\" must be the last " +
                        "parameter in the specification."
                    )

                case _ =>
            }
        }
    }

    def checkOptionalStatus(param: Parameter[_], optionalSpec: Boolean) =
    {
        if (parameters.size > 0)
        {
            if (parameters.last.optional && (! optionalSpec))
                throw new ArgotSpecificationError(
                    "Optional parameter \"" + parameters.last.name +
                    "\" cannot be followed by required parameter \"" +
                    param.placeholderName + "\"")
        }
    }

    def parse(args: Array[String])
    {
        def paddedList(l: List[String], total: Int): List[String] =
        {
            if (l.length >= total)
                l
            else
                l ::: (1 to (total - l.length)).map(i => "").toList
        }

        def parseCompressedShortOpt(optString: String,
                                    optName: String,
                                    a: List[String]):
            List[String] =
        {
            assert(optName.length > 1)
            val (name, rest) = (optName take 1, optName drop 1)
            assert(rest.length > 0)
            val opt = shortNameMap.getOrElse(
                name(0), usage("Unknown option: " + optString)
            )

            val result = opt match
            {
                case o: HasValue[_] =>
                    if (rest.length == 0)
                        usage("Option -" + name + " requires a value.")
                    o.setFromString(rest)
                    a drop 1

                case o: FlagOption[_] =>
                    // It's a flag. thus, the remainder of the option string
                    // consists of the next set of options (e.g., -cvf)
                    o.setByName(name)
                    List("-" + rest) ::: (a drop 1)

                case _ =>
                    throw new ArgotException("(BUG) Found " + opt.getClass +
                                             " in shortNameMap")
            }

            result
        }

        def parseRegularShortOpt(optString: String,
                                 optName: String,
                                 a: List[String]):
            List[String] =
        {
            assert(optName.length == 1)
            val opt = shortNameMap.getOrElse(
                optName(0), usage("Unknown option: " + optString)
            )

            val a2 = a drop 1

            val result = opt match
            {
                case o: HasValue[_] =>
                    if (a2.length == 0)
                        usage("Option " + optString + " requires a value.")
                    o.setFromString(a2(0))
                    a2 drop 1

                case o: FlagOption[_] =>
                    o.setByName(optName)
                    a2

                case _ =>
                    throw new ArgotException("(BUG) Found " + opt.getClass +
                                             " in longNameMap")
            }

            result
        }

        def parseShortOpt(a: List[String]): List[String] =
        {
            val optString = a.take(1)(0)
            assert(optString startsWith "-")
            val optName = optString drop 1

            optName.length match
            {
                case 0 => usage("Missing option name in \"" + optString + "\"")
                case 1 => parseRegularShortOpt(optString, optName, a)
                case _ => parseCompressedShortOpt(optString, optName, a)
            }
        }

        def parseLongOpt(a: List[String]): List[String] =
        {
            val optString = a.take(1)(0)
            assert(optString startsWith "--")
            val optName = optString drop 2
            val opt = longNameMap.getOrElse(
                optName, usage("Unknown option: " + optString)
            )

            val a2 = a drop 1

            val result = opt match
            {
                case o: HasValue[_] =>
                    if (a2.length == 0)
                        usage("Option " + optString + " requires a value.")
                    o.setFromString(a2(0))
                    a2 drop 1

                case o: FlagOption[_] =>
                    o.setByName(optName)
                    a2

                case _ =>
                    throw new ArgotException("(BUG) Found " + opt.getClass +
                                             " in longNameMap")
            }

            result
        }

        @tailrec def doParse(a: List[String]): Unit =
        {
            a match
            {
                case Nil =>
                    parseParams(Nil)

                case "--" :: tail =>
                    parseParams(tail)

                case opt :: tail if (opt.startsWith("--")) =>
                    doParse(parseLongOpt(a))

                case opt :: tail if (opt(0) == '-') =>
                    doParse(parseShortOpt(a))

                case _ =>
                    parseParams(a)
            }
        }

        try
        {
            doParse(args.toList)
        }

        catch
        {
            case e: ArgotConversionException => usage(e.message)
        }
    }

    private def replaceOption(opt: CommandLineOption[_])
    {
        opt.names.filter(_.length == 1).
                  foreach(s => shortNameMap += s(0) -> opt)
        opt.names.filter(_.length > 1).foreach(s => longNameMap += s -> opt)
        allOptions += opt.name -> opt
    }

    private def replaceParameter(param: Parameter[_])
    {
        parameters += param
    }

    private def parseParams(a: List[String]): Unit =
    {
        def parseNext(a: List[String], paramSpecs: List[Parameter[_]]):
            List[String] =
        {
            def checkMissing(paramSpecs: List[Parameter[_]]): List[String] =
            {
                if (paramSpecs.count(! _.optional) > 0)
                    usage("Missing parameter(s): " +
                          paramSpecs.filter(! _.optional).
                                     map(_.name).
                                     mkString(", ")
                    )
                Nil
            }

            paramSpecs match
            {
                case Nil if (a.length > 0) =>
                    usage("Too many parameters.")
                    Nil

                case Nil =>
                    Nil

                case (p: SingleValueParameters[_]) :: tail =>
                    if (a.length == 0)
                        checkMissing(paramSpecs)
                    else
                        a.foreach(s => p.setFromString(s))

                    parseNext(Nil, tail)

                case (p: SingleValueParameter[_]) :: tail =>
                    if (a.length == 0)
                        checkMissing(paramSpecs)
                    else
                        p.setFromString(a.take(1)(0))

                    parseNext(a drop 1, tail)
            }
        }

        parseNext(a, parameters.toList)
    }

    def usageString(message: String = ""): String =
    {
        import grizzled.math.{util => MathUtil}
        import grizzled.string.WordWrapper

        def paramString(p: Parameter[_]): String =
            if (p.optional) "[" + p.name + "]" else p.name

        def optString(name: String, opt: CommandLineOption[_]) =
        {
            val hyphen = if (name.length == 1) "-" else "--"

            opt match
            {
                case ov: SingleValueOption[_] =>
                    hyphen + name + " " + ov.valueName
                case _                      =>
                    hyphen + name
            }
        }

        val mmax = MathUtil.max _

        val lengths =
            for {opt <- allOptions.values
                 name <- opt.names}
            yield
            {
                optString(name, opt).length
            }

        val maxOptLen = mmax(lengths.toSeq: _*)

        val buf = new StringBuilder
        if (message.length > 0)
            buf.append(message + "\n\n")

        buf.append("Usage: " + programName)
        if (allOptions.size > 0)
            buf.append(" [OPTIONS]")

        for (p <- parameters)
        {
            buf.append(" ")
            buf.append(
                p match
                {
                    case _: SingleValueParameter[_]  => paramString(p)
                    case _: SingleValueParameters[_] => paramString(p) + " ..."
                }
            )
        }
            
        buf.append('\n')

        if (allOptions.size > 0)
        {
            buf.append('\n')
            buf.append("OPTIONS\n\n")
            for (key <- allOptions.keySet.toList.sortWith(_ < _))
            {
                val opt = allOptions(key)
                val sorted = opt.names.sortWith(_ < _)
                for (name <- sorted.take(sorted.length - 1))
                    buf.append(optString(name, opt) + "\n")
                val name = sorted.takeRight(1)(0)
                val os = optString(name, opt)
                val padding = (maxOptLen - os.length) + 1 // allow for space
                val prefix = os + (" " * padding)
                val wrapper = new WordWrapper(prefix=prefix)
                val desc = opt match
                {
                    case o: HasValue[_] =>
                        if (o.supportsMultipleValues)
                            o.description +
                            " (May be specified multiple times.)"
                        else
                            o.description

                    case _ =>
                        opt.description
                                  
                }
                buf.append(wrapper.wrap(desc))
                buf.append("\n")
                if (! compactUsage)
                    buf.append("\n")
            }
        }

        buf.toString
    }

    def usage(message: String = "") =
        throw new ArgotUsageException(usageString(message))
}

object ArrrgTest
{
    def main(args: Array[String])
    {
        import ArgotConverters._

        val parser = new ArgotParser("test")
        val iterations = parser.option[Int](List("i", "iterations"), "n",
                                            "Total iterations")
        val sleepTime = parser.option[Int](List("s", "sleep"), "milliseconds",
                                           "Amount to sleep between each run " +
                                           "blah blah blah-de-blah yadda " +
                                           "yadda yadda ya-ya ya blah blah " +
                                           "la-de frickin da")
        val verbose = parser.flag(List("v", "verbose"), true,
                                  "Enable verbose messages")
        val user = parser.multiOption[String](List("u", "user"), "username",
                                          "Name of user to receive " +
                                          "notifications.")
        val email = parser.option[String](List("e", "email"), "emailaddr",
                                          "Addresses to email results")
        {
            (s, opt) =>
            val i = s.indexOf('@')
            if ((i < 1) || (i >= s.length))
                parser.usage("Bad email address")
            s
        }

        val output = parser.parameter[String]("output", "output file", false);
        val input = parser.parameters[Int]("input", "input count", true);

        try
        {
            parser.parse(args)
            println("----------")
            println("iterations=" + iterations.value)
            println("sleepTime=" + sleepTime.value)
            println("verbose=" + verbose.value)
            println("user=" + user.value)
            println("email=" + email.value)
            println("output=" + output.value)
            println("input=" + input.value)
        }

        catch
        {
            case e: ArgotUsageException => println(e.message)
        }
    }
}
