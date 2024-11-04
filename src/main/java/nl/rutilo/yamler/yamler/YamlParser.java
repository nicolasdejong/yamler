package nl.rutilo.yamler.yamler;

import nl.rutilo.yamler.utils.Value;
import nl.rutilo.yamler.yamler.exceptions.YamlerException;

import java.util.List;
import java.util.Map;

import static nl.rutilo.yamler.yamler.YamlTokenizer.TokenType.END;
import static nl.rutilo.yamler.yamler.YamlTokenizer.TokenType.END_DOC;
import static nl.rutilo.yamler.yamler.YamlTokenizer.TokenType.LIST_END;
import static nl.rutilo.yamler.yamler.YamlTokenizer.TokenType.LIST_ITEM;
import static nl.rutilo.yamler.yamler.YamlTokenizer.TokenType.MAP_END;
import static nl.rutilo.yamler.yamler.YamlTokenizer.TokenType.MAP_KEY_FOLLOWS;
import static nl.rutilo.yamler.yamler.YamlTokenizer.TokenType.SEPARATOR;
import static nl.rutilo.yamler.yamler.YamlTokenizer.TokenType.SEPARATOR_KV;

@SuppressWarnings({
  //"squid:S1199" // small blocks are more readable than many small functions
  //"squid:S3776" // high cyclox sometimes preferable over splitting up for complex logic
})
public class YamlParser extends YamlTokenizer {
    final YamlContext context;

    public YamlParser(String yamlText) {
        this(YamlerConfig.DEFAULT, yamlText);
    }
    public YamlParser(YamlerConfig config, String yamlText) {
        super(config, yamlText);
        this.context = new YamlContext();
    }
    private YamlerConfig.ParseInfo createParseInfoFor(final int depth) {
        return new YamlerConfig.ParseInfo() {
            @Override public Object nextObject(boolean deep) {
                return depth < 0 ? null : YamlParser.this.nextObject(depth, deep);
            }
            @Override public byte[] nextBytes(int length) {
                // TODO: implement reading bytes
                if(length != 0) throw error("Trying to read too many bytes: " + length);
                return new byte[0];
            }
            @Override public YamlerException error(String... args) {
                return YamlParser.this.error(args);
            }
        };
    }

    /** Parse() returns an object (null, scalar, list or map) or
      * multiple documents as a list. The list type is Documents.
      */
    public YamlDocuments parse() {
        Value<Object>        result    = Value.empty();
        Value<YamlDocuments> documents = Value.empty();

        try {
            while (!ended()) {
                if(peekToken().type == END_DOC) {
                    while(peekToken().type == END_DOC) nextToken();
                    if(documents.isEmpty()) {
                        documents = Value.of(new YamlDocuments());
                    }
                    result.ifBothPresent(documents, (res, doc) -> doc.add(res));
                    result = Value.empty();
                    context.reset();
                }
                final Object parseResult = nextObject(-1);
                if(!peekToken().isType(END, END_DOC)) throw error("Illegal token: " + peekToken().type);
                if(flowListDepth > 0) throw error("Unterminated list");
                if(flowMapDepth > 0) throw error("Unterminated map");
                result = result.or(parseResult);
            }
        } catch(final YamlerException e) {
            throw e;
        } catch(final RuntimeException rte) {
            rte.printStackTrace();
            throw error(rte.getMessage());
        }

        documents.ifBothPresent(result.notNull(), List::add);

        // Ending with END_DOC will lead to last result = null
        if(result.notNull().isEmpty() && documents.map(List::size).orElse(0) == 1) {
            result = Value.ofNullable(documents.map(d->d.get(0)).or(Value.ofNullable(null)));
        }

        return documents.map(List::size).orElse(0) > 0
            ? documents.get()
            : result.map(YamlDocuments::of)
                    .orElseGet(() -> YamlDocuments.of((Object)null));
    }

    private Object nextObject(int currentMapDepth) { return nextObject(currentMapDepth, true); }
    private Object nextObject(int currentMapDepth, boolean checkIfFollowedByColon) {
        final Token token = peekToken();
        final int depth = token.posInLine;
        final Object result;

        switch(token.type) {
            case LIST_START:
                nextToken();
                result = parseFlowList(currentMapDepth);
                break;
            case LIST_END:
                throw error("LIST_END while not in list");
            case LIST_ITEM:
                result = parseBlockList(currentMapDepth);
                break;
            case MAP_KEY_FOLLOWS:
                nextToken();
                final Object keyVal = peekToken().isType(SEPARATOR, SEPARATOR_KV, MAP_END, LIST_END)
                    ? null : nextObject(depth, /*checkColon=*/false);
                if(depth > currentMapDepth) {
                    result = parseBlockMap(depth, /*firstKey=*/keyVal);
                } else {
                    result = keyVal;
                }
                break;
            case MAP_START:
                nextToken();
                result = parseFlowMap(currentMapDepth);
                break;
            case MAP_END:
                throw error("MAP_END while not in map");
            case SEPARATOR:
                throw error("Unexpected separator");
            case SEPARATOR_KV: // empty key for map
                if(depth == currentMapDepth) { result = null; nextToken(); }
                else result = parseBlockMap(depth, null);
                break;
            case SCALAR:
            case EMPTY:
            case END:
            case END_DOC:
                nextToken();
                result = token.value;
                break;
            case REF:
                nextToken();
                result = nextObject(currentMapDepth, checkIfFollowedByColon);
                context.storeRef(token.value.toString(), result);
                break;
            case USE_REF:
                result = context.getRef(token.value.toString())
                    .orElseThrow(() -> error("Unknown ref: \"" + token.value + "\""));
                nextToken();
                break;
            case TAG:
                nextToken();
                int tdepth =
                    inFlowList ? flowListDepth + 1 :
                    inFlowMap  ? flowMapDepth + 1 : 0;
                result = config.handleTag(token.value.toString(), createParseInfoFor(tdepth));
                break;
            default:
                throw error("Unhandled token:" + token);
        }

        if( checkIfFollowedByColon
            && !inFlowMap
            && (peekToken().type == SEPARATOR_KV)
            && depth > currentMapDepth
            && peekToken().posInLine > currentMapDepth) {
            return parseBlockMap(depth, /*firstKey=*/result);
        }
        return result;
    }

    private boolean isEnd() {
        return peekToken().isType(END, END_DOC);
    }

    private List<?> parseFlowList(int currentMapDepth) {
        final List<Object> list = config.defaultListGenerator.get();
        final boolean oldInFlowList = inFlowList;
        inFlowList = true; // TODO: reset peek token needed?

        while(peekToken().type != LIST_END) {
            // a comma means an empty value
            list.add(peekToken().type == SEPARATOR ? null : nextObject(currentMapDepth));
            if(peekToken().type == LIST_END) break;
            if(peekToken().type != SEPARATOR) throw error("Unexpected token in list: " + peekToken().type);
            nextToken();
        }
        if(peekToken().type == LIST_END) nextToken();

        inFlowList = oldInFlowList;
        return list;
    }
    private List<Object> parseBlockList(int currentMapDepth) {
        final List<Object> list = config.defaultListGenerator.get();
        final boolean oldInFlowList = inFlowList;
        final boolean oldInFlowMap  = inFlowMap;
        inFlowList = false;
        inFlowMap = false;
        final int depth = peekToken().posInLine;

        while(peekToken().type == LIST_ITEM && peekToken().posInLine == depth) {
            nextToken();
            list.add(peekToken().type == LIST_ITEM && peekToken().posInLine == depth ? null : nextObject(currentMapDepth));
        }
        if(peekToken().type == LIST_ITEM && peekToken().posInLine > depth) {
            throw error("Illegal list indent (" + peekToken().posInLine + " > " + currentMapDepth + ")");
        }
        inFlowList = oldInFlowList;
        inFlowMap = oldInFlowMap;
        return list;
    }

    private Map<?,?> parseFlowMap(int currentMapDepth) {
        final Map<Object, Object> map = config.defaultMapGenerator.get();
        final boolean oldInFlowMap = inFlowMap;
        inFlowMap = true;

        while(peekToken().type != MAP_END) {
            if(isEnd()) break;
            // keys can be empty
            final Object key;
            if(peekToken().type == MAP_KEY_FOLLOWS) {
                nextToken();
                key = peekToken().type == MAP_END ? null : nextObject(currentMapDepth, false);
            } else
            if(peekToken().type == SEPARATOR_KV) {
                key = null;
            } else {
                key = nextObject(currentMapDepth, false);
            }

            if(isEnd()) break;
            final Object value;
            if(!peekToken().isType(SEPARATOR_KV, MAP_KEY_FOLLOWS)) {
                value = null;
            } else {
                nextToken(); // skip colon
                value = peekToken().isType(SEPARATOR, MAP_END, MAP_KEY_FOLLOWS) ? null : nextObject(currentMapDepth);
                if(isEnd()) break; // TODO: add test case for this
            }
            map.put(key, value);
            skipIfToken(SEPARATOR);
        }
        if(peekToken().type == MAP_END) nextToken();

        inFlowMap = oldInFlowMap;
        return map;
    }
    private Map<Object,Object> parseBlockMap(int currentMapDepth, Object firstKey) {
        final Map<Object, Object> map = config.defaultMapGenerator.get();
        final boolean oldInFlowList = inFlowList;
        final boolean oldInFlowMap  = inFlowMap;
        inFlowList = false;
        inFlowMap = false;
        Object nextKey = firstKey;

        while(peekToken().posInLine >= currentMapDepth) {
            final Token colonToken = peekToken();
            final Object value;

            // If next is a key, the current key has no value
            if(colonToken.type == MAP_KEY_FOLLOWS) {
                value = null;
            } else

            // If current key is NOT followed by a separator, the current key has no value
            if(colonToken.type != SEPARATOR_KV || colonToken.posInLine < currentMapDepth) {
                value = null;
            } else {
                nextToken();

                // read map value
                final boolean nextIsSeparator = peekToken().isType(SEPARATOR_KV);

                if(nextIsSeparator) {
                    value = null;
                } else
                if(peekToken().posInLine > currentMapDepth) {
                    value = nextObject(currentMapDepth);
                } else {
                    final boolean hasNoValue = peekToken().posInLine <= currentMapDepth
                                          && !(peekToken().posInLine == currentMapDepth && peekToken().type == LIST_ITEM);

                    value = isEnd() || hasNoValue ? null : nextObject(currentMapDepth);
                }
            }

            map.put(nextKey, value);

            nextKey = null;

            if(isEnd()) break;
            else
            if(peekToken().type == SEPARATOR_KV && peekToken().posInLine >= currentMapDepth
                                                && peekToken().posInLine <= colonToken.posInLine) {
                // empty key
            } else {
                if(peekToken().type == LIST_END) {
                    if(flowListDepth == 0) throw error("LIST_END (]) while not in flow list");
                    break;
                }
                if(peekToken().type == MAP_END) {
                    if(flowMapDepth == 0) throw error("MAP_END (}) while not in flow map");
                    break;
                }
                if(peekToken().type == SEPARATOR) break; // comma separator leads to end of map (test for flowDepth?)
                if(peekToken().posInLine > currentMapDepth) throw error("Illegal map indent (" + peekToken().posInLine + " > " + currentMapDepth + ")");
                if(peekToken().posInLine < currentMapDepth) break; // lower indent leads to end of map
                nextKey = nextObject(currentMapDepth, /*checkColon=*/false);
            }
        }
        if(nextKey != null) {
            map.put(nextKey, null);
        }

        inFlowList = oldInFlowList;
        inFlowMap = oldInFlowMap;
        return map;
    }
}
