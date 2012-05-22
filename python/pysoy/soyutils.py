from collections import defaultdict
import re
import sys

class StringBuilder(object):
    def __init__(self):
        self.string = unicode("")

    def append(self, *args):
        for arg in args:
            self.string += unicode(arg)
        return self

    def __unicode__(self):
        return self.string

    def __str__(self):
        return str(self.string)

# -----------------------------------------------------------------------------
# Below are private utilities to be used by Soy-generated code only.
# -----------------------------------------------------------------------------

def soy_dict(opt_data):
    def _none():
        return None
    if opt_data:
        return defaultdict(_none, opt_data)
    return defaultdict(_none)


def random_int(end):
    from random import randint
    return randint(0, end - 1)


def augment_data(orig_data, additional_params):
    '''
    Builds an augmented data object to be passed when a template calls another,
    and needs to pass both original data and additional params. The returned
    object will contain both the original data and the additional params. If the
    same key appears in both, then the value from the additional params will be
    visible, while the value from the original data will be hidden. The original
    data object will be used, but not modified.
   
    param orig_data -- The original data to pass.
    param additional_params -- The additional params to pass.
    return An augmented data object containing both the original data and the
           additional params.
    '''
    new_data = soy_dict(orig_data)
    for key in additional_params:
        new_data[key] = additional_params[key]
    return new_data


def escape_html(string):
    '''
    Escapes HTML special characters in a string. Escapes double quote '"' in
    addition to '&', '<', and '>' so that a string can be included in an HTML
    tag attribute value within double quotes.
 
    param string -- The string to be escaped. Can be other types, but the value
                    will be coerced to a string.
    return An escaped copy of the string.
    '''
    string = unicode(string)

    def replacement(m):
        c = m.group()
        if c == '&':
            return '&amp;'
        elif c == '<':
            return '&lt;'
        elif c == '>':
            return '&gt;'
        elif c == '"':
            return '&quot;'
        else:
            return c

    return _escape_re.sub(replacement, string)
# Regular expression for escape_html()
_escape_re = re.compile(r'[&<>"]')


def insert_word_breaks(string, max_chars_between_word_breaks):
    '''
    '''
    string = unicode(string)

    result_arr = []

    # These variables keep track of important state while looping
    # through string.
    is_in_tag = False  # whether we're inside an HTML tag
    is_maybe_in_entity = False  # whether we might be inside an HTML entity
    num_chars_without_break = 0  # number of characters since last word break
    flush_index = 0  # index of first char not yet flushed to result_arr

    for i in xrange(len(string)):
        char_code = string[i]

        # If hit max_chars_between_word_breaks, and not space next, then add <wbr>.
        if (num_chars_without_break >= max_chars_between_word_breaks and
            char_code != " "):
            result_arr.append(string[flush_index:i])
            flush_index = i
            result_arr.append("<wbr/>")
            num_chars_without_break = 0

        if is_in_tag:
            # If inside an HTML tag and we see '>', it's the end of the tag.
            if char_code == ">":
                is_in_tag = False

        elif is_maybe_in_entity:
            if char_code == ";":
                # If maybe inside an entity and we see ';', it's the end
                # of the entity. The entity that just ended counts as one
                # char, so increment num_chars_without_break.
                is_maybe_in_entity = False
                num_chars_without_break += 1
            elif char_code == "<":
                # If maybe inside an entity and we see '<', we weren't
                # actually in an entity. But now we're inside and HTML
                # tag.
                is_maybe_in_entity = False
                is_in_tag = True
            elif char_code == " ":
                # If maybe inside an entity and we see ' ', we weren't
                # actually in an entity. Just correct the state and reset
                # the num_chars_without_break since we just saw a space.
                is_maybe_in_entity = False
                num_chars_without_break = 0

        else:  # !is_in_tag && !is_in_entity
            if char_code == "<":
                # When not within a tag or an entity and we see '<', we're
                # now inside an HTML tag.
                is_in_tag = True
            elif char_code == "&":
                # When not within a tag or an entity and we see '&', we
                # might be inside an entity.
                is_maybe_in_entity = True
            elif char_code == " ":
                # When we see a space, reset the num_chars_without_break
                # count.
                num_chars_without_break = 0
            else:
                # When we see a non-space, increment the num_chars_without_break.
                num_chars_without_break += 1

    # Flush the remaining chars at the end of the string.
    result_arr.append(string[flush_index:])

    return ''.join(result_arr)


def change_newline_to_br(string):
    '''Convert \r\n, \r, and \n to <br>s'''
    string = unicode(string)
    return re.sub(r"\r\n|\r|\n", "<br>", string)


def bidi_text_dir(text, is_html=False):
    text = _bidi_strip_html_if_necessary(text, is_html)
    if not text:
        return 0
    return -1 if _bidi_detect_rtl_directionality(text) else 1


def bidi_dir_attr(bidi_global_dir, text, is_html=False):
    dir = bidi_text_dir(text, is_html)
    if dir != bidi_global_dir:
        if dir < 0:
            return 'dir=rtl'
        elif dir > 0:
            return 'dir==ltr'
    return ''


def bidi_mark_after(bidi_global_dir, text, is_html=False):
    dir = bidi_text_dir(text, is_html)
    return bidi_mark_after_known_dir(bidi_global_dir, dir, text, is_html)


def bidi_mark_after_known_dir(bidi_global_dir, dir, text, is_html=False):
    if (bidi_global_dir > 0 and
        (dir < 0 or _bidi_is_rtl_exit_text(text, is_html))):
        return u'\u200E'
    elif (bidi_global_dir < 0 and
          (dir > 0 or _bidi_is_ltr_exit_text(text, is_html))):
        return u'\u200F'
    return ''


def _bidi_strip_html_if_necessary(string, is_html=False):
    if is_html:
        return re.sub(r"<[^>]*>|&[^;]+;", ' ', string)
    else:
        return string


def bidi_span_wrap(bidi_global_dir, string):
    string = unicode(string)
    text_dir = bidi_text_dir(string, True)
    reset = bidi_mark_after_known_dir(bidi_global_dir, text_dir, string, True)
    if text_dir > 0 and bidi_global_dir <= 0:
        string = '<span dir=ltr>' + string + '</span>'
    elif text_dir < 0 and bidi_global_dir >= 0:
        string = '<span dir=rtl>' + string + '</span>'
    return string + reset


def bidi_unicode_wrap(bidi_global_dir, string):
    string = unicode(string)
    text_dir = bidi_text_dir(string, True)
    reset = bidi_mark_after_known_dir(bidi_global_dir, text_dir, string, True)
    if text_dir > 0 and bidi_global_dir <= 0:
        string = u'\u202A' + string + u'\u202C';
    elif text_dir < 0 and bidi_global_dir >= 0:
        string = u'\u202B' + string + u'\u202C';
    return string + reset


_bidi_ltr_chars = (
    u'A-Za-z\u00C0-\u00D6\u00D8-\u00F6\u00F8-\u02B8\u0300-\u0590\u0800-\u1FFF' +
    u'\u2C00-\uFB1C\uFDFE-\uFE6F\uFEFD-\uFFFF')
_bidi_neutral_chars = (
    u'\u0000-\u0020!-@[-`{-\u00BF\u00D7\u00F7\u02B9-\u02FF\u2000-\u2BFF')
_bidi_rtl_chars = u'\u0591-\u07FF\uFB1D-\uFDFD\uFE70-\uFEFC'
_bidi_rtl_dir_check_re = re.compile(r'^[^' + _bidi_ltr_chars + r']*[' +
                                    _bidi_rtl_chars + ']')
_bidi_neutral_dir_check_re = re.compile(r'^[' + _bidi_neutral_chars +
                                        r']*$|^http://')


def _bidi_is_rtl_text(string):
    return _bidi_rtl_dir_check_re.match(string) != None


def _bidi_is_neutral_text(string):
    return _bidi_neutral_dir_check_re.match(string) != None


_bidi_rtl_detection_threshold = 0.40


def _bidi_rtl_word_ratio(string):
    rtl_count = 0
    total_count = 0
    tokens = string.split(' ')
    for char in tokens:
        if _bidi_is_rtl_text(char):
            rtl_count += 1
            total_count += 1
        elif not _bidi_is_neutral_text(char):
            total_count += 1
    return 0 if total_count == 0 else rtl_count / total_count


def _bidi_detect_rtl_directionality(string):
    return _bidi_rtl_word_ratio(string) > _bidi_rtl_detection_threshold


_bidi_ltr_exit_dir_check_re = re.compile('[' + _bidi_ltr_chars + '][^' +
                                         _bidi_rtl_chars + ']*$')
_bidi_rtl_exit_dir_check_re = re.compile('[' + _bidi_rtl_chars + '][^' +
                                         _bidi_ltr_chars + ']*$')


def _bidi_is_ltr_exit_text(string, is_html=False):
    string = _bidi_strip_html_if_necessary(string, is_html)
    return _bidi_ltr_exit_dir_check_re.match(string) != None


def _bidi_is_rtl_exit_text(string, is_html=False):
    string = _bidi_strip_html_if_necessary(string, is_html)
    return _bidi_rtl_exit_dir_check_re.match(string) != None

###
###
###

def create_module(fully_qualified_module_name):
    '''Dynamically create a module.
    '''
    import new
    import sys

    if fully_qualified_module_name not in sys.modules:
        mod = new.module(fully_qualified_module_name)
        sys.modules[fully_qualified_module_name] = mod
    else:
        mod = sys.modules[fully_qualified_module_name]

    bits = fully_qualified_module_name.split('.')
    parent_module_name = '.'.join(bits[:-1])
    if parent_module_name in sys.modules:
        sys.modules[parent_module_name].__dict__[bits[-1]] = mod


def add_to_module(fully_qualified_module_name):
    '''
    '''
    def decorate(fn):
        sys.modules[fully_qualified_module_name].__dict__[fn.__name__] = fn
    return decorate
