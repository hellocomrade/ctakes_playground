let negationOffsets = [];
let uncertaintyOffsets = [];
let familyOffsets = [];

let offsetPairsExtra = [];
if(offsetPairs !== '')
{
    let offsetPairsExtraArray = offsetPairs.split(',');
    for (let i = 0; i < offsetPairsExtraArray.length; i++) {
        let tempPairArray = offsetPairsExtraArray[i].split("-");
        offsetPairsExtra.push(tempPairArray[0]);
        offsetPairsExtra.push(tempPairArray[1]);
    }
}

function loadText(outputDivId, additional)
{
    document.querySelector('#' + outputDivId).innerHTML = noteText.replace(/ÊÉ/gi, '<br/>');
    document.querySelector('#' + additional).innerHTML = 'Additional (' + offsetPairsExtra.length / 2 + ')';
}

function highlightPhrases(conceptType, inputDivId, outputDivId, highlightClass)
{
    let offsets = conceptType === 'negation'
                  ? negationOffsets
                  : conceptType === 'uncertainty'
                  ? uncertaintyOffsets
                  : conceptType === 'family'
                  ? familyOffsets
                  : offsetPairsExtra;
    let lastOffset = 0;
    if(inputDivId !== undefined)
        noteText = document.querySelector('#' + inputDivId).textContent.replace(/(<([^>]+)>)/gi, '');
    let outputDivEl = document.querySelector('#' + outputDivId);
    if(offsets.length === 0)
        return;
    outputDivEl.innerHTML = '';
    for (let i = 1; i < offsets.length; lastOffset = offsets[i], i += 2)
    {
        outputDivEl.append(document.createTextNode(noteText.substring(lastOffset, offsets[i - 1]).replace(/ÊÉ/gi, '\n')));
        let spanEl = document.createElement('span');
        spanEl.append(document.createTextNode(noteText.substring(offsets[i - 1], offsets[i]).replace(/ÊÉ/gi, '\n')));
        spanEl.setAttribute('class', highlightClass);
        outputDivEl.append(spanEl);
    }
    outputDivEl.append(document.createTextNode(noteText.substring(lastOffset).replace('\n', '<br/>')));
}

